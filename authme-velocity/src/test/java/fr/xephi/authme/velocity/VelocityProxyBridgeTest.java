package fr.xephi.authme.velocity;

import com.google.common.io.ByteStreams;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.event.EventManager;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.event.player.configuration.PlayerEnterConfigurationEvent;
import com.velocitypowered.api.event.player.configuration.PlayerEnteredConfigurationEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ConnectionRequestBuilder;
import com.velocitypowered.api.proxy.InboundConnection;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.ChannelRegistrar;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import fr.xephi.authme.velocity.events.AuthMeVelocityLoginEvent;
import fr.xephi.authme.velocity.events.AuthMeVelocityLogoutEvent;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.Logger;

import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.WARN)
class VelocityProxyBridgeTest {

    private static final Set<String> COMMANDS =
        Set.of("/login", "/register", "/l", "/reg", "/email", "/captcha", "/2fa", "/totp", "/log");

    @Mock
    private ProxyServer proxyServer;

    @Mock
    private Logger logger;

    @Mock
    private ChannelRegistrar channelRegistrar;

    @Mock
    private PluginMessageEvent pluginMessageEvent;

    @Mock
    private ServerConnection sourceConnection;

    @Mock
    private Player player;

    @Mock
    private ServerConnection currentServer;

    @Mock
    private RegisteredServer authServer;

    @Mock
    private RegisteredServer nonAuthServer;

    @Mock
    private ServerInfo authServerInfo;

    @Mock
    private ServerInfo nonAuthServerInfo;

    @Mock
    private ConnectionRequestBuilder connectionRequest;

    @Mock
    private CommandExecuteEvent commandEvent;

    @Mock
    private PlayerChatEvent chatEvent;

    @Mock
    private CommandSource consoleSource;

    @Mock
    private EventManager eventManager;

    @Captor
    private ArgumentCaptor<byte[]> payloadCaptor;

    @Test
    void shouldRegisterAuthMeChannel() {
        given(proxyServer.getChannelRegistrar()).willReturn(channelRegistrar);

        VelocityProxyBridge bridge = newBridge();
        bridge.registerChannels();

        verify(channelRegistrar).register(VelocityProxyBridge.AUTHME_CHANNEL, VelocityProxyBridge.AUTHME_LEGACY_CHANNEL);
    }

    @Test
    void shouldTrackAuthenticatedPlayerAndForwardPerformLoginOnServerConnect() {
        stubIncomingPluginMessage();
        given(pluginMessageEvent.getData()).willReturn(ProxyTestFixtures.message("login", "Alice"));
        given(sourceConnection.getServer()).willReturn(authServer);
        given(player.getUsername()).willReturn("Alice");
        given(player.getCurrentServer()).willReturn(Optional.of(currentServer));
        stubAuthServer();
        given(currentServer.getServer()).willReturn(authServer);
        given(currentServer.sendPluginMessage(eq(VelocityProxyBridge.AUTHME_CHANNEL), any(byte[].class)))
            .willReturn(true);

        VelocityProxyBridge bridge = newBridge();
        bridge.onPluginMessage(pluginMessageEvent);
        bridge.onServerConnected(new ServerConnectedEvent(player, authServer, null));

        verify(pluginMessageEvent).setResult(any(PluginMessageEvent.ForwardResult.class));
        // Two messages are sent: proxy.started handshake (first) and perform.login (second)
        verify(currentServer, times(2)).sendPluginMessage(eq(VelocityProxyBridge.AUTHME_CHANNEL), payloadCaptor.capture());
        assertPerformLoginPayload(payloadCaptor.getValue(), "alice", "test-secret");
    }

    @Test
    void shouldIgnoreAlreadyHandledPluginMessage() {
        given(pluginMessageEvent.getResult()).willReturn(PluginMessageEvent.ForwardResult.handled());

        VelocityProxyBridge bridge = newBridge();
        bridge.onPluginMessage(pluginMessageEvent);

        verify(pluginMessageEvent, never()).getIdentifier();
        verify(pluginMessageEvent, never()).setResult(any());
    }

    @Test
    void shouldIgnoreUnknownMessageTypes() {
        stubIncomingPluginMessage();
        given(pluginMessageEvent.getData()).willReturn(ProxyTestFixtures.message("unknown-type", "hub"));
        given(player.getUsername()).willReturn("Alice");
        stubAuthServer();

        VelocityProxyBridge bridge = newBridge();
        bridge.onPluginMessage(pluginMessageEvent);
        bridge.onServerConnected(new ServerConnectedEvent(player, authServer, null));

        verify(currentServer, never()).sendPluginMessage(any(), any(byte[].class));
    }

    @Test
    void shouldIgnoreNameBearingMessageWithEmptyPlayerName() {
        stubIncomingPluginMessage();
        given(pluginMessageEvent.getData()).willReturn(ProxyTestFixtures.message("login", ""));
        given(sourceConnection.getServer()).willReturn(authServer);
        stubAuthServer();

        VelocityProxyBridge bridge = newBridge();
        bridge.onPluginMessage(pluginMessageEvent);

        verify(pluginMessageEvent).setResult(PluginMessageEvent.ForwardResult.handled());
        verify(proxyServer, never()).getPlayer(anyString());
    }

    @Test
    void shouldIgnoreMalformedPluginMessagePayload() {
        stubIncomingPluginMessage();
        given(pluginMessageEvent.getData()).willReturn(new byte[] {0});

        VelocityProxyBridge bridge = newBridge();
        bridge.onPluginMessage(pluginMessageEvent);

        verify(pluginMessageEvent).setResult(PluginMessageEvent.ForwardResult.handled());
        verify(proxyServer, never()).getPlayer(anyString());
    }

    @Test
    void shouldKeepSessionWhenDuplicateConnectionWithSameNameDisconnects() {
        stubIncomingPluginMessage();
        given(pluginMessageEvent.getData()).willReturn(ProxyTestFixtures.message("login", "Alice"));
        given(sourceConnection.getServer()).willReturn(authServer);
        given(player.getUsername()).willReturn("Alice");
        given(player.getCurrentServer()).willReturn(Optional.of(currentServer));
        stubAuthServer();
        given(currentServer.getServer()).willReturn(authServer);
        given(currentServer.sendPluginMessage(eq(VelocityProxyBridge.AUTHME_CHANNEL), any(byte[].class)))
            .willReturn(true);
        Player duplicate = mock(Player.class);
        given(duplicate.getUsername()).willReturn("Alice");
        given(proxyServer.getPlayer("Alice")).willReturn(Optional.of(player));

        VelocityProxyBridge bridge = newBridge();
        bridge.onPluginMessage(pluginMessageEvent);
        bridge.onDisconnect(new DisconnectEvent(duplicate, DisconnectEvent.LoginStatus.PRE_SERVER_JOIN));
        bridge.onServerConnected(new ServerConnectedEvent(player, authServer, null));

        verify(currentServer, times(2)).sendPluginMessage(eq(VelocityProxyBridge.AUTHME_CHANNEL), payloadCaptor.capture());
        assertPerformLoginPayload(payloadCaptor.getValue(), "alice", "test-secret");
    }

    @Test
    void shouldDropSessionWhenPlayerDisconnects() {
        stubIncomingPluginMessage();
        given(pluginMessageEvent.getData()).willReturn(ProxyTestFixtures.message("login", "Alice"));
        given(sourceConnection.getServer()).willReturn(authServer);
        given(player.getUsername()).willReturn("Alice");
        stubAuthServer();

        VelocityProxyBridge bridge = newBridge();
        bridge.onPluginMessage(pluginMessageEvent);
        bridge.onDisconnect(new DisconnectEvent(player, DisconnectEvent.LoginStatus.SUCCESSFUL_LOGIN));
        bridge.onServerConnected(new ServerConnectedEvent(player, authServer, null));

        verify(currentServer, never()).sendPluginMessage(any(), any(byte[].class));
    }

    @Test
    void shouldRedirectPlayerOnLogoutWhenConfigured() {
        stubIncomingPluginMessage();
        given(pluginMessageEvent.getData()).willReturn(ProxyTestFixtures.message("logout", "Alice"));
        given(sourceConnection.getServer()).willReturn(nonAuthServer);
        stubNonAuthServer();
        given(proxyServer.getPlayer("alice")).willReturn(Optional.of(player));
        given(proxyServer.getServer("limbo")).willReturn(Optional.of(nonAuthServer));
        given(player.createConnectionRequest(nonAuthServer)).willReturn(connectionRequest);

        VelocityProxyBridge bridge = new VelocityProxyBridge(
            proxyServer, logger, new VelocityProxyConfiguration(Set.of("lobby"), false, true,
                "Authentication required.", true, true, "limbo", true,
                Set.of("/login", "/register"), true, "", "", false),
            new VelocityAuthenticationStore(), null);
        given(proxyServer.getEventManager()).willReturn(eventManager);
        bridge.onPluginMessage(pluginMessageEvent);

        verify(eventManager).fireAndForget(any(AuthMeVelocityLogoutEvent.class));
        verify(connectionRequest).fireAndForget();
    }

    @Test
    void shouldBlockAndNotForwardClientPluginMessageOnAuthMeChannel() {
        given(pluginMessageEvent.getResult()).willReturn(PluginMessageEvent.ForwardResult.forward());
        given(pluginMessageEvent.getIdentifier()).willReturn(VelocityProxyBridge.AUTHME_CHANNEL);
        given(pluginMessageEvent.getSource()).willReturn(player);

        VelocityProxyBridge bridge = newBridge();
        bridge.onPluginMessage(pluginMessageEvent);

        verify(pluginMessageEvent).setResult(PluginMessageEvent.ForwardResult.handled());
    }

    @Test
    void shouldCancelPendingLoginOnExplicitAck() {
        stubIncomingPluginMessage();
        given(pluginMessageEvent.getData()).willReturn(ProxyTestFixtures.message("login", "Alice"));
        given(sourceConnection.getServer()).willReturn(authServer);
        stubAuthServer();
        given(player.getUsername()).willReturn("Alice");
        given(player.getCurrentServer()).willReturn(Optional.of(currentServer));
        given(currentServer.getServer()).willReturn(authServer);
        given(currentServer.sendPluginMessage(eq(VelocityProxyBridge.AUTHME_CHANNEL), any(byte[].class)))
            .willReturn(true);
        given(proxyServer.getPlayer("alice")).willReturn(Optional.of(player));

        VelocityProxyBridge bridge = newBridge();
        given(proxyServer.getEventManager()).willReturn(eventManager);
        bridge.onPluginMessage(pluginMessageEvent);
        bridge.onServerConnected(new ServerConnectedEvent(player, authServer, null));

        // Now backend sends the explicit ACK
        given(pluginMessageEvent.getData()).willReturn(ProxyTestFixtures.message("perform.login.ack", "Alice"));
        bridge.onPluginMessage(pluginMessageEvent);

        // After ACK, proxyServer.getPlayer should have been called twice
        // (one for the AuthMeVelocityLoginEvent and the other by sendAutoLoginIfAlreadySwitched,
        // not by any retry) — the pending login was cancelled before any retry could fire.
        verify(proxyServer, org.mockito.Mockito.times(2)).getPlayer("alice");
        verify(eventManager).fireAndForget(any(AuthMeVelocityLoginEvent.class));
    }

    @Test
    void shouldCancelPendingLoginOnImplicitAckFromNonAuthServer() {
        stubIncomingPluginMessage();
        given(player.getUsername()).willReturn("Alice");
        given(player.getCurrentServer()).willReturn(Optional.of(currentServer));
        stubAuthServer();
        stubNonAuthServer();
        given(currentServer.getServer()).willReturn(authServer);
        given(currentServer.sendPluginMessage(eq(VelocityProxyBridge.AUTHME_CHANNEL), any(byte[].class)))
            .willReturn(true);
        given(proxyServer.getPlayer("alice")).willReturn(Optional.of(player));

        VelocityProxyBridge bridge = newBridge();

        // Mark authenticated via auth server login
        given(pluginMessageEvent.getData()).willReturn(ProxyTestFixtures.message("login", "Alice"));
        given(sourceConnection.getServer()).willReturn(authServer);
        given(proxyServer.getEventManager()).willReturn(eventManager);
        bridge.onPluginMessage(pluginMessageEvent);
        bridge.onServerConnected(new ServerConnectedEvent(player, authServer, null));

        // Backend confirms auto-login with a login message from the non-auth server
        given(pluginMessageEvent.getData()).willReturn(ProxyTestFixtures.message("login", "Alice"));
        given(sourceConnection.getServer()).willReturn(nonAuthServer);
        bridge.onPluginMessage(pluginMessageEvent);

        // Pending is now cancelled; proxyServer.getPlayer was called twice
        // (one for the AuthMeVelocityLoginEvent calling and the other by sendAutoLoginIfAlreadySwitched),
        // not again by any retry.
        verify(proxyServer, org.mockito.Mockito.times(2)).getPlayer("alice");
    }

    @Test
    void shouldNotMarkPlayerAuthenticatedIfLoginComesFromNonAuthServer() {
        stubIncomingPluginMessage();
        given(pluginMessageEvent.getData()).willReturn(ProxyTestFixtures.message("login", "Alice"));
        given(sourceConnection.getServer()).willReturn(nonAuthServer);
        stubNonAuthServer();
        given(player.getUsername()).willReturn("Alice");
        stubAuthServer();

        VelocityProxyBridge bridge = newBridge();
        bridge.onPluginMessage(pluginMessageEvent);
        bridge.onServerConnected(new ServerConnectedEvent(player, authServer, null));

        verify(currentServer, never()).sendPluginMessage(any(), any(byte[].class));
    }

    @Test
    void shouldDenySwitchToNonAuthServerForUnauthenticatedPlayer() {
        given(player.getUsername()).willReturn("Alice");
        given(player.getCurrentServer()).willReturn(Optional.of(currentServer));
        stubNonAuthServer();

        VelocityProxyBridge bridge = newBridge();
        ServerPreConnectEvent event = new ServerPreConnectEvent(player, nonAuthServer, authServer);

        bridge.onServerPreConnect(event);

        assertFalse(event.getResult().isAllowed());
        verify(player).sendMessage(Component.text("Authentication required.", net.kyori.adventure.text.format.NamedTextColor.RED));
    }

    @Test
    void shouldDenySwitchRedirectedAwayFromAuthServerForUnauthenticatedPlayer() {
        given(player.getUsername()).willReturn("Alice");
        stubAuthServer();
        given(player.getCurrentServer()).willReturn(Optional.of(currentServer));
        stubNonAuthServer();

        VelocityProxyBridge bridge = newBridge();
        ServerPreConnectEvent event = new ServerPreConnectEvent(player, authServer, authServer);
        event.setResult(ServerPreConnectEvent.ServerResult.allowed(nonAuthServer));

        bridge.onServerPreConnect(event);

        assertFalse(event.getResult().isAllowed());
    }

    @Test
    void shouldAllowSwitchRedirectedToAuthServerForUnauthenticatedPlayer() {
        given(player.getUsername()).willReturn("Alice");
        stubAuthServer();

        VelocityProxyBridge bridge = newBridge();
        ServerPreConnectEvent event = new ServerPreConnectEvent(player, nonAuthServer);
        event.setResult(ServerPreConnectEvent.ServerResult.allowed(authServer));

        bridge.onServerPreConnect(event);

        assertTrue(event.getResult().isAllowed());
    }

    @Test
    void shouldDisconnectPlayerOnInitialJoinToNonAuthServerWhenSwitchRequiresAuth() {
        given(player.getUsername()).willReturn("Alice");
        given(player.getCurrentServer()).willReturn(Optional.empty());
        stubNonAuthServer();

        VelocityProxyBridge bridge = newBridge();
        ServerPreConnectEvent event = new ServerPreConnectEvent(player, nonAuthServer);

        bridge.onServerPreConnect(event);

        assertFalse(event.getResult().isAllowed());
        verify(player).disconnect(Component.text("Authentication required.", net.kyori.adventure.text.format.NamedTextColor.RED));
    }

    @Test
    void shouldNotForwardPerformLoginForUnauthenticatedPlayer() {
        given(player.getUsername()).willReturn("Alice");
        stubNonAuthServer();

        VelocityProxyBridge bridge = newBridge();
        bridge.onServerConnected(new ServerConnectedEvent(player, nonAuthServer, null));

        verify(currentServer, never()).sendPluginMessage(any(), any(byte[].class));
    }

    @Test
    void shouldFirePremiumLoginEventWhenPremiumPlayerAuthenticatesOnAuthServer() {
        stubIncomingPluginMessage();
        given(pluginMessageEvent.getData()).willReturn(
            ProxyTestFixtures.message("premium.set", "Alice"),
            ProxyTestFixtures.message("login", "Alice"));
        given(sourceConnection.getServer()).willReturn(authServer);
        stubAuthServer();
        given(proxyServer.getPlayer("alice")).willReturn(Optional.of(player));
        given(proxyServer.getEventManager()).willReturn(eventManager);

        VelocityProxyBridge bridge = newBridge();
        bridge.onPluginMessage(pluginMessageEvent);
        bridge.onPluginMessage(pluginMessageEvent);

        ArgumentCaptor<AuthMeVelocityLoginEvent> eventCaptor = ArgumentCaptor.forClass(AuthMeVelocityLoginEvent.class);
        verify(eventManager).fireAndForget(eventCaptor.capture());
        assertSame(player, eventCaptor.getValue().getPlayer());
        assertTrue(eventCaptor.getValue().isPremium());
    }

    @Test
    void shouldForwardPerformLoginWhenSwitchingFromAuthServerToNonAuthServer() {
        stubIncomingPluginMessage();
        given(pluginMessageEvent.getData()).willReturn(ProxyTestFixtures.message("login", "Alice"));
        given(sourceConnection.getServer()).willReturn(authServer);
        stubAuthServer();
        stubNonAuthServer();
        given(player.getUsername()).willReturn("Alice");
        given(player.getCurrentServer()).willReturn(Optional.of(currentServer));
        given(currentServer.getServer()).willReturn(nonAuthServer);
        given(currentServer.sendPluginMessage(eq(VelocityProxyBridge.AUTHME_CHANNEL), any(byte[].class)))
            .willReturn(true);

        VelocityProxyBridge bridge = newBridge();
        bridge.onPluginMessage(pluginMessageEvent);
        bridge.onServerConnected(new ServerConnectedEvent(player, nonAuthServer, authServer));

        verify(currentServer).sendPluginMessage(eq(VelocityProxyBridge.AUTHME_CHANNEL), payloadCaptor.capture());
        assertPerformLoginPayload(payloadCaptor.getValue(), "alice", "test-secret");
    }

    @ParameterizedTest(name = "config-phase auto-login scenario {index}")
    @MethodSource("configPhaseAutoLoginScenarios")
    void shouldSendConfigPhaseAutoLogin(Consumer<VelocityProxyBridgeTest> scenario) {
        scenario.accept(this);
        verify(currentServer).sendPluginMessage(eq(VelocityProxyBridge.AUTHME_CHANNEL), payloadCaptor.capture());
        assertPerformLoginPayload(payloadCaptor.getValue(), "alice", "test-secret");
    }

    private static Stream<Consumer<VelocityProxyBridgeTest>> configPhaseAutoLoginScenarios() {
        return Stream.<Consumer<VelocityProxyBridgeTest>>of(
            // authenticated player entering the auth server
            t -> {
                given(t.player.getUsername()).willReturn("Alice");
                given(t.currentServer.getServer()).willReturn(t.authServer);
                t.stubAuthServer();
                given(t.currentServer.sendPluginMessage(eq(VelocityProxyBridge.AUTHME_CHANNEL), any(byte[].class)))
                    .willReturn(true);
                VelocityProxyBridge bridge = newBridgeWithAuthenticatedStore(t);
                bridge.onPlayerEnteredConfiguration(new PlayerEnteredConfigurationEvent(t.player, t.currentServer));
            },
            // authenticated player switching to a non-auth server
            t -> {
                given(t.player.getUsername()).willReturn("Alice");
                given(t.currentServer.getServer()).willReturn(t.nonAuthServer);
                t.stubNonAuthServer();
                given(t.currentServer.sendPluginMessage(eq(VelocityProxyBridge.AUTHME_CHANNEL), any(byte[].class)))
                    .willReturn(true);
                VelocityProxyBridge bridge = newBridgeWithAuthenticatedStore(t);
                bridge.onPlayerEnterConfiguration(new PlayerEnterConfigurationEvent(t.player, t.currentServer));
            }
        );
    }

    @ParameterizedTest(name = "no config-phase auto-login scenario {index}")
    @MethodSource("noConfigPhaseAutoLoginScenarios")
    void shouldNotSendConfigPhaseAutoLogin(Consumer<VelocityProxyBridgeTest> scenario) {
        scenario.accept(this);
        verify(currentServer, never()).sendPluginMessage(any(), any(byte[].class));
    }

    private static Stream<Consumer<VelocityProxyBridgeTest>> noConfigPhaseAutoLoginScenarios() {
        return Stream.<Consumer<VelocityProxyBridgeTest>>of(
            // unauthenticated player entering the auth server
            t -> {
                given(t.player.getUsername()).willReturn("Alice");
                given(t.currentServer.getServer()).willReturn(t.authServer);
                t.stubAuthServer();
                t.newBridge().onPlayerEnteredConfiguration(new PlayerEnteredConfigurationEvent(t.player, t.currentServer));
            },
            // authenticated player entering a non-auth server normally
            t -> {
                given(t.player.getUsername()).willReturn("Alice");
                given(t.currentServer.getServer()).willReturn(t.nonAuthServer);
                t.stubNonAuthServer();
                newBridgeWithAuthenticatedStore(t)
                    .onPlayerEnteredConfiguration(new PlayerEnteredConfigurationEvent(t.player, t.currentServer));
            },
            // entered configuration without a server connection
            t -> newBridgeWithAuthenticatedStore(t)
                .onPlayerEnteredConfiguration(new PlayerEnteredConfigurationEvent(t.player, null))
        );
    }

    private static VelocityProxyBridge newBridgeWithAuthenticatedStore(VelocityProxyBridgeTest test) {
        VelocityAuthenticationStore store = new VelocityAuthenticationStore();
        store.markAuthenticated("alice");
        return new VelocityProxyBridge(test.proxyServer, test.logger,
            ProxyTestFixtures.configuration(true, false, COMMANDS), store, null);
    }

    // --- Command blocking tests ---

    @Test
    void shouldBlockNonWhitelistedCommandForUnauthenticatedPlayer() {
        given(commandEvent.getCommandSource()).willReturn(player);
        given(commandEvent.getCommand()).willReturn("spawn");
        given(player.getUsername()).willReturn("alice");
        given(player.getCurrentServer()).willReturn(Optional.of(currentServer));
        given(currentServer.getServer()).willReturn(authServer);
        stubAuthServer();

        VelocityProxyBridge bridge = newBridge();
        bridge.onCommandExecute(commandEvent);

        verify(commandEvent).setResult(CommandExecuteEvent.CommandResult.denied());
    }

    @ParameterizedTest(name = "allow command scenario {index}")
    @MethodSource("allowedCommandScenarios")
    void shouldAllowCommandWhenNotBlocked(Function<VelocityProxyBridgeTest, VelocityProxyBridge> scenario) {
        VelocityProxyBridge bridge = scenario.apply(this);
        bridge.onCommandExecute(commandEvent);

        verify(commandEvent, never()).setResult(any());
    }

    private static Stream<Function<VelocityProxyBridgeTest, VelocityProxyBridge>> allowedCommandScenarios() {
        return Stream.<Function<VelocityProxyBridgeTest, VelocityProxyBridge>>of(
            // whitelisted command from an unauthenticated player on the auth server
            t -> {
                given(t.commandEvent.getCommandSource()).willReturn(t.player);
                given(t.commandEvent.getCommand()).willReturn("login secret");
                given(t.player.getUsername()).willReturn("alice");
                given(t.player.getCurrentServer()).willReturn(Optional.of(t.currentServer));
                given(t.currentServer.getServer()).willReturn(t.authServer);
                t.stubAuthServer();
                return t.newBridge();
            },
            // already authenticated player
            t -> {
                given(t.commandEvent.getCommandSource()).willReturn(t.player);
                given(t.player.getUsername()).willReturn("alice");
                given(t.player.getCurrentServer()).willReturn(Optional.of(t.currentServer));
                given(t.currentServer.getServer()).willReturn(t.authServer);
                t.stubAuthServer();
                return newBridgeWithAuthenticatedStore(t);
            },
            // player connected to a non-auth server
            t -> {
                given(t.commandEvent.getCommandSource()).willReturn(t.player);
                given(t.player.getCurrentServer()).willReturn(Optional.of(t.currentServer));
                given(t.currentServer.getServer()).willReturn(t.nonAuthServer);
                t.stubNonAuthServer();
                return t.newBridge();
            },
            // player with no current server
            t -> {
                given(t.commandEvent.getCommandSource()).willReturn(t.player);
                given(t.player.getCurrentServer()).willReturn(Optional.empty());
                return t.newBridge();
            },
            // command source is not a player
            t -> {
                given(t.commandEvent.getCommandSource()).willReturn(t.consoleSource);
                return t.newBridge();
            },
            // command blocking disabled in the configuration
            t -> {
                VelocityProxyConfiguration config = new VelocityProxyConfiguration(
                    Set.of("lobby"), false, true, "Authentication required.", false, false, "",
                    false, Set.of("/login"), true, "", "", false);
                return new VelocityProxyBridge(t.proxyServer, t.logger, config,
                    new VelocityAuthenticationStore(), null);
            }
        );
    }

    // --- Chat blocking tests ---

    @Test
    void shouldBlockChatForUnauthenticatedPlayer() {
        given(chatEvent.getPlayer()).willReturn(player);
        given(player.getUsername()).willReturn("alice");
        given(player.getCurrentServer()).willReturn(Optional.of(currentServer));
        given(currentServer.getServer()).willReturn(authServer);
        stubAuthServer();

        VelocityProxyBridge bridge = newBridge();
        bridge.onPlayerChat(chatEvent);

        verify(chatEvent).setResult(PlayerChatEvent.ChatResult.denied());
    }

    @ParameterizedTest(name = "allow chat scenario {index}")
    @MethodSource("allowedChatScenarios")
    void shouldAllowChatWhenNotBlocked(Function<VelocityProxyBridgeTest, VelocityProxyBridge> scenario) {
        VelocityProxyBridge bridge = scenario.apply(this);
        bridge.onPlayerChat(chatEvent);

        verify(chatEvent, never()).setResult(any());
    }

    private static Stream<Function<VelocityProxyBridgeTest, VelocityProxyBridge>> allowedChatScenarios() {
        return Stream.<Function<VelocityProxyBridgeTest, VelocityProxyBridge>>of(
            // already authenticated player on the auth server
            t -> {
                given(t.chatEvent.getPlayer()).willReturn(t.player);
                given(t.player.getUsername()).willReturn("alice");
                given(t.player.getCurrentServer()).willReturn(Optional.of(t.currentServer));
                given(t.currentServer.getServer()).willReturn(t.authServer);
                t.stubAuthServer();
                return newBridgeWithAuthenticatedStore(t);
            },
            // player connected to a non-auth server
            t -> {
                given(t.chatEvent.getPlayer()).willReturn(t.player);
                given(t.player.getCurrentServer()).willReturn(Optional.of(t.currentServer));
                given(t.currentServer.getServer()).willReturn(t.nonAuthServer);
                t.stubNonAuthServer();
                return t.newBridge();
            },
            // player with no current server
            t -> {
                given(t.chatEvent.getPlayer()).willReturn(t.player);
                given(t.player.getCurrentServer()).willReturn(Optional.empty());
                return t.newBridge();
            },
            // chat blocking disabled in the configuration
            t -> {
                VelocityProxyConfiguration config = new VelocityProxyConfiguration(
                    Set.of("lobby"), false, true, "Authentication required.", false, false, "",
                    true, Set.of("/login"), false, "", "", false);
                return new VelocityProxyBridge(t.proxyServer, t.logger, config,
                    new VelocityAuthenticationStore(), null);
            }
        );
    }

    @Test
    void shouldForceOnlineModeAfterChunkedPremiumListResync() {
        stubIncomingPluginMessage();
        given(pluginMessageEvent.getData()).willReturn(ProxyTestFixtures.chunkPayload(0, true, "Alice"));
        given(sourceConnection.getServer()).willReturn(authServer);
        stubAuthServer();

        VelocityProxyBridge bridge = newBridge();
        bridge.onPluginMessage(pluginMessageEvent);

        PreLoginEvent event = new PreLoginEvent(mock(InboundConnection.class), "Alice", null);
        bridge.onPreLogin(event);

        assertEquals(PreLoginEvent.PreLoginComponentResult.forceOnlineMode().toString(), event.getResult().toString());
    }

    @Test
    void shouldDiscardPremiumListWithMissingChunk() {
        stubIncomingPluginMessage();
        given(pluginMessageEvent.getData())
            .willReturn(ProxyTestFixtures.chunkPayload(0, true, "Alice"), ProxyTestFixtures.chunkPayload(0, false, "Bob"),
                ProxyTestFixtures.chunkPayload(2, true, "Carol"));
        given(sourceConnection.getServer()).willReturn(authServer);
        stubAuthServer();

        VelocityProxyBridge bridge = newBridge();
        bridge.onPluginMessage(pluginMessageEvent);
        bridge.onPluginMessage(pluginMessageEvent);
        bridge.onPluginMessage(pluginMessageEvent);

        PreLoginEvent aliceLogin = new PreLoginEvent(mock(InboundConnection.class), "Alice", null);
        bridge.onPreLogin(aliceLogin);
        PreLoginEvent carolLogin = new PreLoginEvent(mock(InboundConnection.class), "Carol", null);
        bridge.onPreLogin(carolLogin);

        assertEquals(PreLoginEvent.PreLoginComponentResult.forceOnlineMode().toString(), aliceLogin.getResult().toString());
        assertEquals(PreLoginEvent.PreLoginComponentResult.allowed().toString(), carolLogin.getResult().toString());
    }

    @Test
    void shouldCancelPendingPremiumVerificationWhenForcedOnlineLoginNeverCompletes() {
        stubIncomingPluginMessage();
        given(pluginMessageEvent.getData()).willReturn(ProxyTestFixtures.message("premium.pending.set", "Alice"));
        given(sourceConnection.getServer()).willReturn(authServer);
        stubAuthServer();

        VelocityProxyBridge bridge = newBridge();
        bridge.onPluginMessage(pluginMessageEvent);

        PreLoginEvent firstAttempt = new PreLoginEvent(mock(InboundConnection.class), "Alice", null);
        bridge.onPreLogin(firstAttempt);
        // Mojang rejected the unlicensed client; Velocity fires no event for that, the player just reconnects
        PreLoginEvent secondAttempt = new PreLoginEvent(mock(InboundConnection.class), "Alice", null);
        bridge.onPreLogin(secondAttempt);
        PreLoginEvent thirdAttempt = new PreLoginEvent(mock(InboundConnection.class), "Alice", null);
        bridge.onPreLogin(thirdAttempt);

        assertEquals(PreLoginEvent.PreLoginComponentResult.forceOnlineMode().toString(), firstAttempt.getResult().toString());
        assertEquals(PreLoginEvent.PreLoginComponentResult.allowed().toString(), secondAttempt.getResult().toString());
        assertEquals(PreLoginEvent.PreLoginComponentResult.allowed().toString(), thirdAttempt.getResult().toString());
    }

    @Test
    void shouldPersistNonChunkedPremiumListToCache(@TempDir Path tempDir) throws Exception {
        stubIncomingPluginMessage();
        given(pluginMessageEvent.getData()).willReturn(ProxyTestFixtures.listPayload("Alice"));
        given(sourceConnection.getServer()).willReturn(authServer);
        stubAuthServer();

        VelocityProxyBridge bridge = new VelocityProxyBridge(proxyServer, logger, ProxyTestFixtures.configuration(true, false, COMMANDS), new VelocityAuthenticationStore(), tempDir);
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

        VelocityProxyBridge restarted = new VelocityProxyBridge(proxyServer, logger, ProxyTestFixtures.configuration(true, false, COMMANDS), new VelocityAuthenticationStore(), tempDir);
        PreLoginEvent event = new PreLoginEvent(mock(InboundConnection.class), "Alice", null);
        restarted.onPreLogin(event);
        restarted.shutdown();

        assertEquals(PreLoginEvent.PreLoginComponentResult.forceOnlineMode().toString(), event.getResult().toString());
    }

    private void stubAuthServer() {
        given(authServer.getServerInfo()).willReturn(authServerInfo);
        given(authServerInfo.getName()).willReturn("lobby");
    }

    private void stubNonAuthServer() {
        given(nonAuthServer.getServerInfo()).willReturn(nonAuthServerInfo);
        given(nonAuthServerInfo.getName()).willReturn("survival");
    }

    private VelocityProxyBridge newBridge() {
        return new VelocityProxyBridge(proxyServer, logger,
            ProxyTestFixtures.configuration(true, false, COMMANDS), new VelocityAuthenticationStore(), null);
    }

    private void stubIncomingPluginMessage() {
        given(pluginMessageEvent.getResult()).willReturn(PluginMessageEvent.ForwardResult.forward());
        given(pluginMessageEvent.getIdentifier()).willReturn(VelocityProxyBridge.AUTHME_CHANNEL);
        given(pluginMessageEvent.getSource()).willReturn(sourceConnection);
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
