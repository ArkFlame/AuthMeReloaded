package fr.xephi.authme.velocity;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class VelocityIssue3159Test {

    @Test
    void shouldMarkPlayerAuthenticatedOnAutoLoginAckAndStopBlockingChat() {
        ProxyServer proxyServer = mock(ProxyServer.class);
        Logger logger = mock(Logger.class);
        PluginMessageEvent event = mock(PluginMessageEvent.class);
        ServerConnection source = mock(ServerConnection.class);
        RegisteredServer authServer = mock(RegisteredServer.class);
        ServerInfo serverInfo = mock(ServerInfo.class);
        Player player = mock(Player.class);
        ServerConnection currentServer = mock(ServerConnection.class);
        PlayerChatEvent chatEvent = mock(PlayerChatEvent.class);
        VelocityAuthenticationStore store = new VelocityAuthenticationStore();

        given(event.getResult()).willReturn(PluginMessageEvent.ForwardResult.forward());
        given(event.getIdentifier()).willReturn(VelocityProxyBridge.AUTHME_CHANNEL);
        given(event.getSource()).willReturn(source);
        given(event.getData()).willReturn(createMessage("perform.login.ack", "Alice"));
        given(source.getServer()).willReturn(authServer);
        given(authServer.getServerInfo()).willReturn(serverInfo);
        given(serverInfo.getName()).willReturn("lobby");
        given(chatEvent.getPlayer()).willReturn(player);
        given(player.getUsername()).willReturn("Alice");
        given(player.getCurrentServer()).willReturn(Optional.of(currentServer));
        given(currentServer.getServer()).willReturn(authServer);

        VelocityProxyBridge bridge = new VelocityProxyBridge(
            proxyServer, logger, createConfiguration(true), store, null);
        bridge.onPluginMessage(event);

        assertTrue(store.isAuthenticated("Alice"), "the ACK must mark the auto-logged-in player authenticated");

        bridge.onPlayerChat(chatEvent);
        verify(chatEvent, never()).setResult(any());
    }

    @Test
    void shouldNotMarkPlayerAuthenticatedOnAckWhenAutoLoginIsDisabled() {
        ProxyServer proxyServer = mock(ProxyServer.class);
        Logger logger = mock(Logger.class);
        PluginMessageEvent event = mock(PluginMessageEvent.class);
        ServerConnection source = mock(ServerConnection.class);
        RegisteredServer authServer = mock(RegisteredServer.class);
        ServerInfo serverInfo = mock(ServerInfo.class);
        VelocityAuthenticationStore store = new VelocityAuthenticationStore();

        given(event.getResult()).willReturn(PluginMessageEvent.ForwardResult.forward());
        given(event.getIdentifier()).willReturn(VelocityProxyBridge.AUTHME_CHANNEL);
        given(event.getSource()).willReturn(source);
        given(event.getData()).willReturn(createMessage("perform.login.ack", "Alice"));
        given(source.getServer()).willReturn(authServer);
        given(authServer.getServerInfo()).willReturn(serverInfo);
        given(serverInfo.getName()).willReturn("lobby");

        VelocityProxyBridge bridge = new VelocityProxyBridge(
            proxyServer, logger, createConfiguration(false), store, null);
        bridge.onPluginMessage(event);

        assertFalse(store.isAuthenticated("Alice"));
    }

    private static byte[] createMessage(String typeId, String playerName) {
        ByteArrayDataOutput output = ByteStreams.newDataOutput();
        output.writeUTF(typeId);
        output.writeUTF(playerName);
        return output.toByteArray();
    }

    private static VelocityProxyConfiguration createConfiguration(boolean autoLogin) {
        return new VelocityProxyConfiguration(Set.of("lobby"), false, true,
            "Authentication required.", autoLogin, false, "", true,
            Set.of("/login"), true, "", "test-secret", false);
    }
}
