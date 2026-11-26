package fr.xephi.authme.bungee;

import com.google.common.io.ByteStreams;
import fr.xephi.authme.bungee.events.AuthMeBungeeLoginEvent;
import fr.xephi.authme.bungee.events.AuthMeBungeeLogoutEvent;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.PendingConnection;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.event.ChatEvent;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.PreLoginEvent;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.event.ServerConnectEvent;
import net.md_5.bungee.api.event.ServerSwitchEvent;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.plugin.PluginManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.WARN)
class BungeeProxyBridgeTest {

    @Mock
    private ProxyServer proxyServer;

    @Mock
    private Logger logger;

    @Mock
    private PluginMessageEvent pluginMessageEvent;

    @Mock
    private Server sourceServer;

    @Mock
    private ProxiedPlayer player;

    @Mock
    private Server currentServer;

    @Mock
    private ServerInfo authServerInfo;

    @Mock
    private ServerInfo nonAuthServerInfo;

    @Mock
    private ServerSwitchEvent serverSwitchEvent;

    @Mock
    private PlayerDisconnectEvent playerDisconnectEvent;

    @Mock
    private ChatEvent commandEvent;

    @Mock
    private ChatEvent chatEvent;

    @Mock
    private ServerConnectEvent serverConnectEvent;

    @Mock
    private PreLoginEvent preLoginEvent;

    @Mock
    private PendingConnection pendingConnection;

    @Mock
    private PluginManager pluginManager;

    @Mock
    private Plugin packetEventsPlugin;

    @Captor
    private ArgumentCaptor<byte[]> payloadCaptor;

    @BeforeEach
    void registerPacketEventsOnTheProxy() {
        given(proxyServer.getPluginManager()).willReturn(pluginManager);
        given(pluginManager.getPlugin("packetevents")).willReturn(packetEventsPlugin);
    }

    @Test
    void shouldTrackAuthenticatedPlayerAndForwardPerformLoginOnServerSwitch() {
        stubIncomingPluginMessage();
        given(pluginMessageEvent.getData()).willReturn(ProxyTestFixtures.message("login", "Alice"));
        given(sourceServer.getInfo()).willReturn(authServerInfo);
        given(serverSwitchEvent.getPlayer()).willReturn(player);
        given(player.getName()).willReturn("Alice");
        given(player.getServer()).willReturn(currentServer);
        given(currentServer.getInfo()).willReturn(authServerInfo);
        given(authServerInfo.getName()).willReturn("lobby");

        BungeeProxyBridge bridge = newBridge();
        bridge.onPluginMessage(pluginMessageEvent);
        bridge.onServerSwitch(serverSwitchEvent);

        verify(authServerInfo).sendData(eq(BungeeProxyBridge.AUTHME_CHANNEL), payloadCaptor.capture(), eq(false));
        assertPerformLoginPayload(payloadCaptor.getValue(), "alice", "test-secret");
        verify(pluginMessageEvent).setCancelled(true);
    }

    @Test
    void shouldNotForwardBackendPremiumListToClient() {
        stubIncomingPluginMessage();
        given(pluginMessageEvent.getData()).willReturn(ProxyTestFixtures.message("premium.list.chunk", "0:1:notch,jeb_"));

        BungeeProxyBridge bridge = newBridge();
        bridge.onPluginMessage(pluginMessageEvent);

        verify(pluginMessageEvent).setCancelled(true);
    }

    @Test
    void shouldIgnoreUnrelatedPluginMessages() {
        stubIncomingPluginMessage();
        given(pluginMessageEvent.getData()).willReturn(ProxyTestFixtures.message("unknown-type", "hub"));
        given(serverSwitchEvent.getPlayer()).willReturn(player);
        given(player.getName()).willReturn("Alice");
        given(player.getServer()).willReturn(currentServer);
        given(currentServer.getInfo()).willReturn(authServerInfo);
        given(authServerInfo.getName()).willReturn("lobby");
        given(serverSwitchEvent.getFrom()).willReturn(authServerInfo);

        BungeeProxyBridge bridge = newBridge();
        bridge.onPluginMessage(pluginMessageEvent);
        bridge.onServerSwitch(serverSwitchEvent);

        verify(authServerInfo, never()).sendData(any(String.class), any(byte[].class), eq(false));
    }

    @Test
    void shouldDropSessionWhenPlayerDisconnects() {
        stubIncomingPluginMessage();
        given(pluginMessageEvent.getData()).willReturn(ProxyTestFixtures.message("login", "Alice"));
        given(sourceServer.getInfo()).willReturn(authServerInfo);
        given(playerDisconnectEvent.getPlayer()).willReturn(player);
        given(serverSwitchEvent.getPlayer()).willReturn(player);
        given(player.getName()).willReturn("Alice");
        given(player.getServer()).willReturn(currentServer);
        given(currentServer.getInfo()).willReturn(authServerInfo);
        given(authServerInfo.getName()).willReturn("lobby");

        BungeeProxyBridge bridge = newBridge();
        bridge.onPluginMessage(pluginMessageEvent);
        bridge.onPlayerDisconnect(playerDisconnectEvent);
        bridge.onServerSwitch(serverSwitchEvent);

        verify(authServerInfo, never()).sendData(any(String.class), any(byte[].class), eq(false));
    }

    @Test
    void shouldRedirectPlayerOnLogoutWhenConfigured() {
        stubIncomingPluginMessage();
        given(pluginMessageEvent.getData()).willReturn(ProxyTestFixtures.message("logout", "Alice"));
        given(proxyServer.getPlayer("alice")).willReturn(player);
        given(proxyServer.getServerInfo("limbo")).willReturn(nonAuthServerInfo);

        stubEventsAllowed();
        BungeeProxyBridge bridge = new BungeeProxyBridge(
            proxyServer, logger, new BungeeProxyConfiguration(
                Set.of("lobby"), false, true, Set.of("/login"), true, true,
                "Authentication required.", true, true, "limbo", "", "", false),
            new BungeeAuthenticationStore(), null);
        bridge.onPluginMessage(pluginMessageEvent);

        verify(player).connect(nonAuthServerInfo);
    }

    @Test
    void shouldCancelClientPluginMessageToPreventForwarding() {
        given(pluginMessageEvent.isCancelled()).willReturn(false);
        given(pluginMessageEvent.getTag()).willReturn(BungeeProxyBridge.AUTHME_CHANNEL);
        given(pluginMessageEvent.getSender()).willReturn(player);

        BungeeProxyBridge bridge = newBridge();
        bridge.onPluginMessage(pluginMessageEvent);

        verify(pluginMessageEvent).setCancelled(true);
    }

    @Test
    void shouldCancelPendingLoginOnExplicitAck() {
        stubIncomingPluginMessage();
        given(pluginMessageEvent.getData()).willReturn(ProxyTestFixtures.message("login", "Alice"));
        given(sourceServer.getInfo()).willReturn(authServerInfo);
        given(authServerInfo.getName()).willReturn("lobby");
        given(serverSwitchEvent.getPlayer()).willReturn(player);
        given(player.getName()).willReturn("Alice");
        given(player.getServer()).willReturn(currentServer);
        given(currentServer.getInfo()).willReturn(authServerInfo);
        given(serverSwitchEvent.getFrom()).willReturn(null);

        BungeeProxyBridge bridge = newBridge();
        bridge.onPluginMessage(pluginMessageEvent);
        bridge.onServerSwitch(serverSwitchEvent);

        // Backend sends explicit ACK
        given(pluginMessageEvent.getData()).willReturn(ProxyTestFixtures.message("perform.login.ack", "Alice"));
        bridge.onPluginMessage(pluginMessageEvent);

        // getPlayer called twice: once by login event lookup, once by sendAutoLoginIfAlreadySwitched — not by any retry
        verify(proxyServer, org.mockito.Mockito.times(2)).getPlayer("alice");
    }

    @Test
    void shouldCancelPendingLoginOnImplicitAckFromNonAuthServer() {
        stubIncomingPluginMessage();
        given(authServerInfo.getName()).willReturn("lobby");
        given(nonAuthServerInfo.getName()).willReturn("survival");
        given(serverSwitchEvent.getPlayer()).willReturn(player);
        given(player.getName()).willReturn("Alice");
        given(player.getServer()).willReturn(currentServer);
        given(currentServer.getInfo()).willReturn(authServerInfo);

        BungeeProxyBridge bridge = newBridge();

        // Mark authenticated via auth server login
        given(pluginMessageEvent.getData()).willReturn(ProxyTestFixtures.message("login", "Alice"));
        given(sourceServer.getInfo()).willReturn(authServerInfo);
        bridge.onPluginMessage(pluginMessageEvent);
        bridge.onServerSwitch(serverSwitchEvent);

        // Backend confirms auto-login with login from non-auth server
        given(pluginMessageEvent.getData()).willReturn(ProxyTestFixtures.message("login", "Alice"));
        given(sourceServer.getInfo()).willReturn(nonAuthServerInfo);
        bridge.onPluginMessage(pluginMessageEvent);

        // getPlayer called twice: once by login event lookup, once by sendAutoLoginIfAlreadySwitched — not by retries
        verify(proxyServer, org.mockito.Mockito.times(2)).getPlayer("alice");
    }

    @Test
    void shouldNotMarkPlayerAuthenticatedIfLoginComesFromNonAuthServer() {
        stubIncomingPluginMessage();
        given(pluginMessageEvent.getData()).willReturn(ProxyTestFixtures.message("login", "Alice"));
        given(sourceServer.getInfo()).willReturn(nonAuthServerInfo);
        given(nonAuthServerInfo.getName()).willReturn("survival");
        given(serverSwitchEvent.getPlayer()).willReturn(player);
        given(player.getName()).willReturn("Alice");
        given(player.getServer()).willReturn(currentServer);
        given(currentServer.getInfo()).willReturn(authServerInfo);
        given(authServerInfo.getName()).willReturn("lobby");

        BungeeProxyBridge bridge = newBridge();
        bridge.onPluginMessage(pluginMessageEvent);
        bridge.onServerSwitch(serverSwitchEvent);

        verify(authServerInfo, never()).sendData(any(String.class), any(byte[].class), eq(false));
    }

    @Test
    void shouldBlockNonWhitelistedCommandForUnauthenticatedPlayer() {
        given(commandEvent.isCancelled()).willReturn(false);
        given(commandEvent.isCommand()).willReturn(true);
        given(commandEvent.getSender()).willReturn(player);
        given(commandEvent.getMessage()).willReturn("/spawn");
        given(player.getName()).willReturn("Alice");
        given(player.getServer()).willReturn(currentServer);
        given(currentServer.getInfo()).willReturn(authServerInfo);
        given(authServerInfo.getName()).willReturn("lobby");

        BungeeProxyBridge bridge = newBridge();
        bridge.onCommand(commandEvent);

        verify(commandEvent).setCancelled(true);
    }

    @Test
    void shouldAllowWhitelistedCommandForUnauthenticatedPlayer() {
        given(commandEvent.isCancelled()).willReturn(false);
        given(commandEvent.isCommand()).willReturn(true);
        given(commandEvent.getSender()).willReturn(player);
        given(commandEvent.getMessage()).willReturn("/login secret");
        given(player.getName()).willReturn("Alice");
        given(player.getServer()).willReturn(currentServer);
        given(currentServer.getInfo()).willReturn(authServerInfo);
        given(authServerInfo.getName()).willReturn("lobby");

        BungeeProxyBridge bridge = newBridge();
        bridge.onCommand(commandEvent);

        verify(commandEvent, never()).setCancelled(true);
    }

    @Test
    void shouldBlockChatForUnauthenticatedPlayer() {
        given(chatEvent.isCancelled()).willReturn(false);
        given(chatEvent.isCommand()).willReturn(false);
        given(chatEvent.getSender()).willReturn(player);
        given(player.getName()).willReturn("Alice");
        given(player.getServer()).willReturn(currentServer);
        given(currentServer.getInfo()).willReturn(authServerInfo);
        given(authServerInfo.getName()).willReturn("lobby");

        BungeeProxyBridge bridge = newBridge();
        bridge.onPlayerChat(chatEvent);

        verify(chatEvent).setCancelled(true);
    }

    @Test
    void shouldDenySwitchToNonAuthServerForUnauthenticatedPlayer() {
        given(serverConnectEvent.isCancelled()).willReturn(false);
        given(serverConnectEvent.getPlayer()).willReturn(player);
        given(serverConnectEvent.getTarget()).willReturn(nonAuthServerInfo);
        given(nonAuthServerInfo.getName()).willReturn("survival");
        given(player.getName()).willReturn("Alice");
        given(player.getServer()).willReturn(currentServer);

        BungeeProxyBridge bridge = newBridge();
        bridge.onPlayerConnectingToServer(serverConnectEvent);

        verify(serverConnectEvent).setCancelled(true);
        verify(player).sendMessage(any(net.md_5.bungee.api.chat.TextComponent.class));
    }

    @Test
    void shouldDisconnectPlayerOnInitialJoinToNonAuthServerWhenSwitchRequiresAuth() {
        given(serverConnectEvent.isCancelled()).willReturn(false);
        given(serverConnectEvent.getPlayer()).willReturn(player);
        given(serverConnectEvent.getTarget()).willReturn(nonAuthServerInfo);
        given(nonAuthServerInfo.getName()).willReturn("survival");
        given(player.getName()).willReturn("Alice");
        given(player.getServer()).willReturn(null);

        BungeeProxyBridge bridge = newBridge();
        bridge.onPlayerConnectingToServer(serverConnectEvent);

        verify(serverConnectEvent).setCancelled(true);
        verify(player).disconnect(any(net.md_5.bungee.api.chat.TextComponent.class));
    }

    @Test
    void shouldNotForwardPerformLoginToNonAuthServers() {
        stubIncomingPluginMessage();
        given(pluginMessageEvent.getData()).willReturn(ProxyTestFixtures.message("login", "Alice"));
        given(sourceServer.getInfo()).willReturn(authServerInfo);
        given(authServerInfo.getName()).willReturn("lobby");
        given(serverSwitchEvent.getPlayer()).willReturn(player);
        given(player.getServer()).willReturn(currentServer);
        given(currentServer.getInfo()).willReturn(nonAuthServerInfo);
        given(nonAuthServerInfo.getName()).willReturn("survival");

        BungeeProxyBridge bridge = newBridge();
        bridge.onPluginMessage(pluginMessageEvent);
        bridge.onServerSwitch(serverSwitchEvent);

        verify(nonAuthServerInfo, never()).sendData(any(String.class), any(byte[].class), eq(false));
    }

    @Test
    void shouldForwardPerformLoginWhenLeavingAuthServer() {
        stubIncomingPluginMessage();
        given(pluginMessageEvent.getData()).willReturn(ProxyTestFixtures.message("login", "Alice"));
        given(sourceServer.getInfo()).willReturn(authServerInfo);
        given(serverSwitchEvent.getPlayer()).willReturn(player);
        given(player.getName()).willReturn("Alice");
        given(player.getServer()).willReturn(currentServer);
        given(currentServer.getInfo()).willReturn(nonAuthServerInfo);
        given(nonAuthServerInfo.getName()).willReturn("survival");
        given(serverSwitchEvent.getFrom()).willReturn(authServerInfo);
        given(authServerInfo.getName()).willReturn("lobby");

        BungeeProxyBridge bridge = newBridge();
        bridge.onPluginMessage(pluginMessageEvent);
        bridge.onServerSwitch(serverSwitchEvent);

        verify(nonAuthServerInfo).sendData(eq(BungeeProxyBridge.AUTHME_CHANNEL), payloadCaptor.capture(), eq(false));
        assertPerformLoginPayload(payloadCaptor.getValue(), "alice", "test-secret");
    }

    @Test
    void shouldSendProxyStartedHandshakeToAuthServerOnStartup() {
        given(proxyServer.getServers()).willReturn(Map.of("lobby", authServerInfo));
        given(authServerInfo.getName()).willReturn("lobby");
        given(authServerInfo.getPlayers()).willReturn(List.of(player));

        BungeeProxyBridge bridge = newBridge();
        bridge.broadcastProxyStartedHandshake();

        verify(authServerInfo).sendData(eq(BungeeProxyBridge.AUTHME_CHANNEL), payloadCaptor.capture(), eq(false));
        com.google.common.io.ByteArrayDataInput in = ByteStreams.newDataInput(payloadCaptor.getValue());
        assertEquals("proxy.started", in.readUTF());
        assertEquals("bungee", in.readUTF());
    }

    @Test
    void shouldDeferProxyStartedHandshakeWhenNoPlayersOnAuthServer() {
        given(proxyServer.getServers()).willReturn(Map.of("lobby", authServerInfo));
        given(authServerInfo.getName()).willReturn("lobby");
        given(authServerInfo.getPlayers()).willReturn(List.of());
        given(serverSwitchEvent.getPlayer()).willReturn(player);
        given(player.getName()).willReturn("Alice");
        given(player.getServer()).willReturn(currentServer);
        given(currentServer.getInfo()).willReturn(authServerInfo);

        BungeeProxyBridge bridge = newBridge();
        bridge.broadcastProxyStartedHandshake();

        // No handshake sent yet (no players at startup)
        verify(authServerInfo, never()).sendData(any(String.class), any(byte[].class), eq(false));

        // First player connection to that auth server triggers the deferred handshake
        given(authServerInfo.getPlayers()).willReturn(List.of(player));
        bridge.onServerSwitch(serverSwitchEvent);

        verify(authServerInfo).sendData(eq(BungeeProxyBridge.AUTHME_CHANNEL), payloadCaptor.capture(), eq(false));
        com.google.common.io.ByteArrayDataInput in = ByteStreams.newDataInput(payloadCaptor.getValue());
        assertEquals("proxy.started", in.readUTF());
        assertEquals("bungee", in.readUTF());
    }

    @Test
    void shouldSendAutoLoginImmediatelyWhenPlayerAlreadySwitchedBeforeLoginMessage() {
        stubIncomingPluginMessage();
        given(pluginMessageEvent.getData()).willReturn(ProxyTestFixtures.message("login", "Alice"));
        given(sourceServer.getInfo()).willReturn(authServerInfo);
        given(authServerInfo.getName()).willReturn("lobby");
        // Player is already on a non-auth server when the login message arrives
        given(proxyServer.getPlayer("alice")).willReturn(player);
        given(player.getServer()).willReturn(currentServer);
        given(currentServer.getInfo()).willReturn(nonAuthServerInfo);
        given(nonAuthServerInfo.getName()).willReturn("survival");

        stubEventsAllowed();
        BungeeProxyBridge bridge = newBridge();
        bridge.onPluginMessage(pluginMessageEvent);

        verify(nonAuthServerInfo).sendData(eq(BungeeProxyBridge.AUTHME_CHANNEL), payloadCaptor.capture(), eq(false));
        assertPerformLoginPayload(payloadCaptor.getValue(), "alice", "test-secret");
    }

    @Test
    void shouldForceOnlineModeForPremiumHandshakeWhenOfflineCompatibilityDisabled() {
        stubIncomingPluginMessage();
        given(pluginMessageEvent.getData()).willReturn(ProxyTestFixtures.message("premium.set", "Alice"));
        given(preLoginEvent.getConnection()).willReturn(pendingConnection);
        given(pendingConnection.getName()).willReturn("Alice");
        given(pendingConnection.isOnlineMode()).willReturn(false);

        BungeeProxyBridge bridge = newBridge();
        bridge.onPluginMessage(pluginMessageEvent);
        bridge.onPreLogin(preLoginEvent);

        verify(pendingConnection).setOnlineMode(true);
    }

    @Test
    void shouldForceOnlineModeForPendingPremiumEnrollmentAtPreLogin() {
        stubIncomingPluginMessage();
        given(pluginMessageEvent.getData()).willReturn(ProxyTestFixtures.message("premium.pending.set", "Alice"));
        given(preLoginEvent.getConnection()).willReturn(pendingConnection);
        given(pendingConnection.getName()).willReturn("Alice");
        given(pendingConnection.isOnlineMode()).willReturn(false);

        BungeeProxyBridge bridge = newBridge();
        bridge.onPluginMessage(pluginMessageEvent);
        bridge.onPreLogin(preLoginEvent);

        verify(pendingConnection).setOnlineMode(true);
    }

    @Test
    void shouldForceOnlineModeForPremiumHandshakeAfterChunkedPremiumListResync() {
        stubIncomingPluginMessage();
        given(pluginMessageEvent.getData()).willReturn(ProxyTestFixtures.chunkPayload(0, true, "Alice"));
        given(preLoginEvent.getConnection()).willReturn(pendingConnection);
        given(pendingConnection.getName()).willReturn("Alice");
        given(pendingConnection.isOnlineMode()).willReturn(false);

        BungeeProxyBridge bridge = newBridge();
        bridge.onPluginMessage(pluginMessageEvent);
        bridge.onPreLogin(preLoginEvent);

        verify(pendingConnection).setOnlineMode(true);
    }

    @Test
    void shouldDiscardPremiumListWithMissingChunk() {
        stubIncomingPluginMessage();
        given(pluginMessageEvent.getData())
            .willReturn(ProxyTestFixtures.chunkPayload(0, true, "Alice"), ProxyTestFixtures.chunkPayload(0, false, "Bob"),
                ProxyTestFixtures.chunkPayload(2, true, "Carol"));
        given(preLoginEvent.getConnection()).willReturn(pendingConnection);
        given(pendingConnection.getName()).willReturn("Alice");
        given(pendingConnection.isOnlineMode()).willReturn(false);

        BungeeProxyBridge bridge = newBridge();
        bridge.onPluginMessage(pluginMessageEvent);
        bridge.onPluginMessage(pluginMessageEvent);
        bridge.onPluginMessage(pluginMessageEvent);
        bridge.onPreLogin(preLoginEvent);

        verify(pendingConnection).setOnlineMode(true);
    }

    @Test
    void shouldFireLoginEventWhenPlayerAuthenticatesOnAuthServer() {
        stubIncomingPluginMessage();
        given(pluginMessageEvent.getData()).willReturn(ProxyTestFixtures.message("login", "Alice"));
        given(sourceServer.getInfo()).willReturn(authServerInfo);
        given(authServerInfo.getName()).willReturn("lobby");
        given(proxyServer.getPlayer("alice")).willReturn(player);
        given(player.getServer()).willReturn(currentServer);
        given(currentServer.getInfo()).willReturn(authServerInfo);

        stubEventsAllowed();

        BungeeProxyBridge bridge = newBridge();
        bridge.onPluginMessage(pluginMessageEvent);

        ArgumentCaptor<AuthMeBungeeLoginEvent> eventCaptor = ArgumentCaptor.forClass(AuthMeBungeeLoginEvent.class);
        verify(pluginManager).callEvent(eventCaptor.capture());
        assertSame(player, eventCaptor.getValue().getPlayer());
        assertFalse(eventCaptor.getValue().isPremium());
    }

    @Test
    void shouldFirePremiumLoginEventWhenPremiumPlayerAuthenticatesOnAuthServer() {
        stubIncomingPluginMessage();
        given(pluginMessageEvent.getData()).willReturn(
            ProxyTestFixtures.message("premium.set", "Alice"),
            ProxyTestFixtures.message("login", "Alice"));
        given(sourceServer.getInfo()).willReturn(authServerInfo);
        given(authServerInfo.getName()).willReturn("lobby");
        given(proxyServer.getPlayer("alice")).willReturn(player);
        given(player.getServer()).willReturn(currentServer);
        given(currentServer.getInfo()).willReturn(authServerInfo);

        stubEventsAllowed();

        BungeeProxyBridge bridge = newBridge();
        bridge.onPluginMessage(pluginMessageEvent);
        bridge.onPluginMessage(pluginMessageEvent);

        ArgumentCaptor<AuthMeBungeeLoginEvent> eventCaptor = ArgumentCaptor.forClass(AuthMeBungeeLoginEvent.class);
        verify(pluginManager).callEvent(eventCaptor.capture());
        assertSame(player, eventCaptor.getValue().getPlayer());
        assertTrue(eventCaptor.getValue().isPremium());
    }

    @Test
    void shouldFireLogoutEventWhenPlayerLogsOut() {
        stubIncomingPluginMessage();
        given(pluginMessageEvent.getData()).willReturn(ProxyTestFixtures.message("logout", "Alice"));
        given(proxyServer.getPlayer("alice")).willReturn(player);

        stubEventsAllowed();

        BungeeProxyBridge bridge = newBridge();
        bridge.onPluginMessage(pluginMessageEvent);

        ArgumentCaptor<AuthMeBungeeLogoutEvent> eventCaptor = ArgumentCaptor.forClass(AuthMeBungeeLogoutEvent.class);
        verify(pluginManager).callEvent(eventCaptor.capture());
        assertSame(player, eventCaptor.getValue().getPlayer());
    }

    @Test
    void shouldPersistNonChunkedPremiumListToCache(@TempDir Path tempDir) throws Exception {
        stubIncomingPluginMessage();
        given(pluginMessageEvent.getData()).willReturn(ProxyTestFixtures.listPayload("Alice"));

        BungeeProxyBridge bridge = new BungeeProxyBridge(proxyServer, logger, ProxyTestFixtures.configuration(), new BungeeAuthenticationStore(), tempDir);
        bridge.onPluginMessage(pluginMessageEvent);

        Path cacheFile = tempDir.resolve("premium_names.cache");
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            if (Files.exists(cacheFile) && Files.readString(cacheFile).contains("alice")) {
                break;
            }
            Thread.sleep(50);
        }
        assertTrue(Files.exists(cacheFile), "premium cache file should be written");
        bridge.shutdown();

        BungeeProxyBridge restarted = new BungeeProxyBridge(proxyServer, logger, ProxyTestFixtures.configuration(), new BungeeAuthenticationStore(), tempDir);
        given(preLoginEvent.getConnection()).willReturn(pendingConnection);
        given(pendingConnection.getName()).willReturn("Alice");
        given(pendingConnection.isOnlineMode()).willReturn(false);
        restarted.onPreLogin(preLoginEvent);
        restarted.shutdown();

        verify(pendingConnection).setOnlineMode(true);
    }

    private void stubEventsAllowed() {
        given(proxyServer.getPluginManager()).willReturn(pluginManager);
        given(pluginManager.callEvent(any())).willAnswer(inv -> inv.getArgument(0));
    }

    private BungeeProxyBridge newBridge() {
        return new BungeeProxyBridge(proxyServer, logger,
            ProxyTestFixtures.configuration(), new BungeeAuthenticationStore(), null);
    }

    private void stubIncomingPluginMessage() {
        given(pluginMessageEvent.isCancelled()).willReturn(false);
        given(pluginMessageEvent.getTag()).willReturn(BungeeProxyBridge.AUTHME_CHANNEL);
        given(pluginMessageEvent.getSender()).willReturn(sourceServer);
    }

    private static void assertPerformLoginPayload(byte[] payload, String expectedPlayerName, String sharedSecret) {
        com.google.common.io.ByteArrayDataInput in = ByteStreams.newDataInput(payload);
        assertEquals("perform.login", in.readUTF());
        assertEquals(expectedPlayerName, in.readUTF());
        long timestamp = in.readLong();
        assertEquals("", in.readUTF());
        String hmac = in.readUTF();
        assertTrue(Math.abs(System.currentTimeMillis() - timestamp) < 5000L, "timestamp should be recent");
        assertEquals(ProxyMessageSecurity.computeHmac(sharedSecret, expectedPlayerName, timestamp, null), hmac);
    }
}
