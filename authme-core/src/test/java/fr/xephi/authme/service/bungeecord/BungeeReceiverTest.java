package fr.xephi.authme.service.bungeecord;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import fr.xephi.authme.AuthMe;
import fr.xephi.authme.TestHelper;
import fr.xephi.authme.data.ProxySessionManager;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.process.Management;
import fr.xephi.authme.security.HashUtils;
import fr.xephi.authme.service.BukkitService;
import fr.xephi.authme.service.ProxyLoginRequestValidator;
import fr.xephi.authme.settings.Settings;
import fr.xephi.authme.settings.properties.HooksSettings;
import fr.xephi.authme.settings.properties.PremiumSettings;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.Messenger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockMakers;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.UUID;

import static fr.xephi.authme.service.BukkitServiceTestHelper.setBukkitServiceToRunTaskAsynchronously;
import static fr.xephi.authme.service.BukkitServiceTestHelper.setBukkitServiceToScheduleSyncEntityTaskFromOptionallyAsyncTask;
import static fr.xephi.authme.service.BukkitServiceTestHelper.setBukkitServiceToScheduleSyncTaskFromOptionallyAsyncTask;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.WARN)
class BungeeReceiverTest {

    private static final String SHARED_SECRET = "test-secret";
    private static final String PLAYER_NAME = "Bobby";

    @Mock(mockMaker = MockMakers.INLINE)
    private AuthMe plugin;

    @Mock(mockMaker = MockMakers.INLINE)
    private BukkitService bukkitService;

    @Mock(mockMaker = MockMakers.INLINE)
    private ProxySessionManager proxySessionManager;

    @Mock(mockMaker = MockMakers.INLINE)
    private Management management;

    @Mock(mockMaker = MockMakers.INLINE)
    private BungeeSender bungeeSender;

    @Mock(mockMaker = MockMakers.INLINE)
    private DataSource dataSource;

    @Mock(mockMaker = MockMakers.INLINE)
    private ProxyLoginRequestValidator proxyLoginRequestValidator;

    @Mock(mockMaker = MockMakers.INLINE)
    private Settings settings;

    @Mock(mockMaker = MockMakers.INLINE)
    private Server server;

    @Mock(mockMaker = MockMakers.INLINE)
    private Messenger messenger;

    @BeforeAll
    static void initLogger() {
        TestHelper.setupLogger();
    }

    @BeforeEach
    void setUp() {
        given(plugin.getServer()).willReturn(server);
        given(server.getMessenger()).willReturn(messenger);
        given(settings.getProperty(PremiumSettings.ENABLE_PREMIUM)).willReturn(true);
    }

    @Test
    void shouldRegisterIncomingChannelWhenEnabled() {
        given(settings.getProperty(HooksSettings.BUNGEECORD)).willReturn(true);
        given(messenger.isIncomingChannelRegistered(plugin, "authme:main")).willReturn(false);

        newReceiver();

        verify(messenger).registerIncomingPluginChannel(eq(plugin), eq("authme:main"), any(BungeeReceiver.class));
    }

    @Test
    void shouldUnregisterIncomingChannelWhenDisabledOnReload() {
        given(settings.getProperty(HooksSettings.BUNGEECORD)).willReturn(true, false);
        given(messenger.isIncomingChannelRegistered(plugin, "authme:main")).willReturn(false, true);

        BungeeReceiver bungeeReceiver = newReceiver();
        bungeeReceiver.reload(settings);

        verify(messenger).registerIncomingPluginChannel(plugin, "authme:main", bungeeReceiver);
        verify(messenger).unregisterIncomingPluginChannel(plugin, "authme:main", bungeeReceiver);
    }

    @Test
    void shouldQueueSessionAndForceLoginWhenPerformLoginReceivedForOnlinePlayer() {
        // given
        long timestamp = System.currentTimeMillis();
        String hmac = HashUtils.hmacSha256(SHARED_SECRET, PLAYER_NAME + ":" + timestamp + ":");
        givenBungeeReceiverReady();

        Player player = mock(Player.class, withSettings().mockMaker(MockMakers.INLINE));
        given(player.getName()).willReturn(PLAYER_NAME);
        given(player.isOnline()).willReturn(true);
        given(bukkitService.getPlayerExact(PLAYER_NAME)).willReturn(player);
        given(proxyLoginRequestValidator.validate(player, null)).willReturn(true);

        BungeeReceiver receiver = newReceiver();

        byte[] payload = buildPerformLoginPayload(PLAYER_NAME, timestamp, hmac);

        // when
        receiver.onPluginMessageReceived("authme:main", player, payload);

        // then
        verify(proxySessionManager).processProxySessionMessage(PLAYER_NAME, null);
        verify(proxySessionManager).removeLoginRequest(PLAYER_NAME);
        verify(management).forceLoginFromProxy(player);
        verify(bungeeSender).sendAuthMeBungeecordMessage(player, MessageType.PERFORM_LOGIN_ACK);
    }

    @Test
    void shouldOnlyQueueSessionWhenPerformLoginReceivedForOfflinePlayer() {
        // given
        long timestamp = System.currentTimeMillis();
        String hmac = HashUtils.hmacSha256(SHARED_SECRET, PLAYER_NAME + ":" + timestamp + ":");
        givenBungeeReceiverReady();
        given(bukkitService.getPlayerExact(PLAYER_NAME)).willReturn(null);

        BungeeReceiver receiver = newReceiver();

        Player carrier = mock(Player.class, withSettings().mockMaker(MockMakers.INLINE));
        byte[] payload = buildPerformLoginPayload(PLAYER_NAME, timestamp, hmac);

        // when
        receiver.onPluginMessageReceived("authme:main", carrier, payload);

        // then
        verify(proxySessionManager).processProxySessionMessage(PLAYER_NAME, null);
        verify(management, never()).forceLoginFromProxy(any(Player.class));
        verify(bungeeSender, never()).sendAuthMeBungeecordMessage(any(), any());
    }

    @Test
    void shouldFinalizePremiumLoginWhenValidateAcceptsVerifiedUuid() {
        // given
        UUID verifiedUuid = UUID.fromString("8d6d0684-d8b4-4d40-8d2d-0dd4df5555c8");
        long timestamp = System.currentTimeMillis();
        String hmac = HashUtils.hmacSha256(SHARED_SECRET, PLAYER_NAME + ":" + timestamp + ":" + verifiedUuid);
        givenBungeeReceiverReady();
        setBukkitServiceToRunTaskAsynchronously(bukkitService);
        setBukkitServiceToScheduleSyncEntityTaskFromOptionallyAsyncTask(bukkitService);

        Player player = mock(Player.class, withSettings().mockMaker(MockMakers.INLINE));
        given(player.isOnline()).willReturn(true);
        given(bukkitService.getPlayerExact(PLAYER_NAME)).willReturn(player);
        given(proxyLoginRequestValidator.validate(player, verifiedUuid)).willReturn(true);

        BungeeReceiver receiver = newReceiver();

        byte[] payload = buildPerformLoginPayload(PLAYER_NAME, timestamp, verifiedUuid.toString(), hmac);

        // when
        receiver.onPluginMessageReceived("authme:main", player, payload);

        // then
        verify(proxySessionManager).processProxySessionMessage(PLAYER_NAME, verifiedUuid);
        verify(management).forceLoginFromProxy(player);
        verify(bungeeSender).sendAuthMeBungeecordMessage(player, MessageType.PERFORM_LOGIN_ACK);
    }

    @Test
    void shouldRemoveQueuedRequestWhenPremiumValidateRejects() {
        // given
        UUID verifiedUuid = UUID.fromString("8d6d0684-d8b4-4d40-8d2d-0dd4df5555c8");
        long timestamp = System.currentTimeMillis();
        String hmac = HashUtils.hmacSha256(SHARED_SECRET, PLAYER_NAME + ":" + timestamp + ":" + verifiedUuid);
        givenBungeeReceiverReady();
        setBukkitServiceToRunTaskAsynchronously(bukkitService);

        Player player = mock(Player.class, withSettings().mockMaker(MockMakers.INLINE));
        given(player.isOnline()).willReturn(true);
        given(bukkitService.getPlayerExact(PLAYER_NAME)).willReturn(player);
        given(proxyLoginRequestValidator.validate(player, verifiedUuid)).willReturn(false);

        BungeeReceiver receiver = newReceiver();

        byte[] payload = buildPerformLoginPayload(PLAYER_NAME, timestamp, verifiedUuid.toString(), hmac);

        // when
        receiver.onPluginMessageReceived("authme:main", player, payload);

        // then
        verify(proxySessionManager).removeLoginRequest(PLAYER_NAME);
        verify(management, never()).forceLoginFromProxy(any(Player.class));
        verify(bungeeSender, never()).sendAuthMeBungeecordMessage(any(), any());
    }

    @Test
    void shouldValidateAndQueueConfigPhasePerformLogin() {
        // given
        long timestamp = System.currentTimeMillis();
        String hmac = HashUtils.hmacSha256(SHARED_SECRET, PLAYER_NAME + ":" + timestamp + ":");

        given(settings.getProperty(HooksSettings.BUNGEECORD)).willReturn(true);
        given(settings.getProperty(HooksSettings.PROXY_SHARED_SECRET)).willReturn(SHARED_SECRET);

        BungeeReceiver receiver = newReceiver();

        byte[] payload = buildPerformLoginPayload(PLAYER_NAME, timestamp, hmac);

        // when
        String result = receiver.handleConfigPhasePerformLogin(payload);

        // then
        assertThat(result, equalTo("bobby"));
        verify(proxySessionManager).processProxySessionMessage(PLAYER_NAME, null);
        verify(management, never()).forceLoginFromProxy(any(Player.class));
    }

    @Test
    void shouldRejectConfigPhasePerformLoginWithInvalidHmac() {
        // given
        long timestamp = System.currentTimeMillis();

        given(settings.getProperty(HooksSettings.BUNGEECORD)).willReturn(true);
        given(settings.getProperty(HooksSettings.PROXY_SHARED_SECRET)).willReturn(SHARED_SECRET);

        BungeeReceiver receiver = newReceiver();

        byte[] payload = buildPerformLoginPayload(PLAYER_NAME, timestamp, "not-a-valid-hmac");

        // when
        String result = receiver.handleConfigPhasePerformLogin(payload);

        // then
        assertThat(result, nullValue());
        verify(proxySessionManager, never()).processProxySessionMessage(any(), any());
    }

    @Test
    void shouldSendEmptyPremiumListOnProxyStartedWhenPremiumIsDisabled() {
        // given
        given(settings.getProperty(PremiumSettings.ENABLE_PREMIUM)).willReturn(false);
        Player carrier = mock(Player.class, withSettings().mockMaker(MockMakers.INLINE));
        BungeeReceiver receiver = receiverForProxyStartedNotification(carrier);

        // when
        receiver.onPluginMessageReceived("authme:main", carrier, buildProxyStartedPayload("velocity"));

        // then
        verify(dataSource, never()).getPremiumUsernames();
        verify(bungeeSender).sendPremiumList(carrier, List.of());
    }

    @Test
    void shouldSendEmptyPremiumListOnProxyStartedWhenNoPremiumUsersAreStored() {
        // given
        given(dataSource.getPremiumUsernames()).willReturn(List.of());
        Player carrier = mock(Player.class, withSettings().mockMaker(MockMakers.INLINE));
        BungeeReceiver receiver = receiverForProxyStartedNotification(carrier);

        // when
        receiver.onPluginMessageReceived("authme:main", carrier, buildProxyStartedPayload("velocity"));

        // then
        verify(bungeeSender).sendPremiumList(carrier, List.of());
    }

    /** Sets up the common stub state for a proxy-started notification with one online player. */
    private BungeeReceiver receiverForProxyStartedNotification(Player carrier) {
        given(settings.getProperty(HooksSettings.BUNGEECORD)).willReturn(true);
        setBukkitServiceToRunTaskAsynchronously(bukkitService);
        setBukkitServiceToScheduleSyncTaskFromOptionallyAsyncTask(bukkitService);
        given(bukkitService.getOnlinePlayers()).willReturn(List.of(carrier));
        return newReceiver();
    }

    @Test
    void shouldSendPremiumListViaTriggeringPlayerWhenNoOnlinePlayers() {
        // given
        given(settings.getProperty(HooksSettings.BUNGEECORD)).willReturn(true);
        given(dataSource.getPremiumUsernames()).willReturn(List.of("Alice"));
        setBukkitServiceToRunTaskAsynchronously(bukkitService);
        setBukkitServiceToScheduleSyncTaskFromOptionallyAsyncTask(bukkitService);
        given(bukkitService.getOnlinePlayers()).willReturn(List.of());

        Player triggeringPlayer = mock(Player.class, withSettings().mockMaker(MockMakers.INLINE));

        BungeeReceiver receiver = newReceiver();

        // when
        receiver.onPluginMessageReceived("authme:main", triggeringPlayer, buildProxyStartedPayload("velocity"));

        // then
        verify(bungeeSender).sendPremiumList(triggeringPlayer, List.of("Alice"));
    }

    /** Stubs the channel and shared-secret state used by perform.login messages. */
    private void givenBungeeReceiverReady() {
        given(settings.getProperty(HooksSettings.BUNGEECORD)).willReturn(true);
        given(settings.getProperty(HooksSettings.PROXY_SHARED_SECRET)).willReturn(SHARED_SECRET);
        given(messenger.isIncomingChannelRegistered(plugin, "authme:main")).willReturn(false);
    }

    private BungeeReceiver newReceiver() {
        return new BungeeReceiver(plugin, bukkitService, proxySessionManager, management, bungeeSender, dataSource,
            proxyLoginRequestValidator, settings);
    }

    private static byte[] buildProxyStartedPayload(String proxyName) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF(MessageType.PROXY_STARTED.getId());
        out.writeUTF(proxyName);
        return out.toByteArray();
    }

    private static byte[] buildPerformLoginPayload(String playerName, long timestamp, String hmac) {
        return buildPerformLoginPayload(playerName, timestamp, "", hmac);
    }

    private static byte[] buildPerformLoginPayload(String playerName, long timestamp, String uuid, String hmac) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF(MessageType.PERFORM_LOGIN.getId());
        out.writeUTF(playerName);
        out.writeLong(timestamp);
        out.writeUTF(uuid);
        out.writeUTF(hmac);
        return out.toByteArray();
    }
}
