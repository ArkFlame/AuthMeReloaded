package fr.xephi.authme.velocity;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.GameProfileRequestEvent;
import com.velocitypowered.api.event.player.configuration.PlayerEnteredConfigurationEvent;
import com.velocitypowered.api.proxy.InboundConnection;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import com.velocitypowered.api.util.GameProfile;
import com.velocitypowered.api.util.UuidUtils;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.Logger;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class VelocityIssue3090Test {

    private static final UUID PREMIUM_UUID = UUID.fromString("8d6d0684-d8b4-4d40-8d2d-0dd4df5555c8");

    @Test
    void shouldSendPremiumUuidInPerformLoginToNonAuthBackendDuringConfiguration() {
        Fixture fixture = new Fixture();
        fixture.recordPremiumName();
        fixture.verifyProfileRequestRewritesToOfflineUuid();

        fixture.bridge.onPlayerEnteredConfiguration(
            new PlayerEnteredConfigurationEvent(fixture.player, fixture.currentServer));

        assertPerformLoginCarriesPremiumUuid(fixture.currentServer);
    }

    private static void assertPerformLoginCarriesPremiumUuid(ServerConnection connection) {
        ArgumentCaptor<byte[]> payload = ArgumentCaptor.forClass(byte[].class);
        verify(connection).sendPluginMessage(eq(VelocityProxyBridge.AUTHME_CHANNEL), payload.capture());
        ByteArrayDataInput input = ByteStreams.newDataInput(payload.getValue());
        assertEquals("perform.login", input.readUTF());
        assertEquals("alice", input.readUTF());
        input.readLong();
        assertEquals(PREMIUM_UUID.toString(), input.readUTF());
        assertEquals(ProxyMessageSecurity.computeHmac("test-secret", "alice",
            ProxyTestFixtures.timestamp(payload.getValue()), PREMIUM_UUID), input.readUTF());
    }

    private static final class Fixture {
        private final ProxyServer proxyServer = mock(ProxyServer.class);
        private final Logger logger = mock(Logger.class);
        private final PluginMessageEvent pluginMessageEvent = mock(PluginMessageEvent.class);
        private final ServerConnection source = mock(ServerConnection.class);
        private final RegisteredServer authServer = mock(RegisteredServer.class);
        private final ServerInfo authServerInfo = mock(ServerInfo.class);
        private final RegisteredServer gameServer = mock(RegisteredServer.class);
        private final ServerInfo gameServerInfo = mock(ServerInfo.class);
        private final Player player = mock(Player.class);
        private final ServerConnection currentServer = mock(ServerConnection.class);
        private final VelocityProxyBridge bridge;

        private Fixture() {
            given(authServer.getServerInfo()).willReturn(authServerInfo);
            given(authServerInfo.getName()).willReturn("lobby");
            given(gameServer.getServerInfo()).willReturn(gameServerInfo);
            given(gameServerInfo.getName()).willReturn("survival");
            given(player.getUsername()).willReturn("Alice");
            given(currentServer.getServer()).willReturn(gameServer);
            given(currentServer.sendPluginMessage(eq(VelocityProxyBridge.AUTHME_CHANNEL), any(byte[].class)))
                .willReturn(true);
            bridge = new VelocityProxyBridge(proxyServer, logger,
                ProxyTestFixtures.configuration(true, true), new VelocityAuthenticationStore(), null);
        }

        private void recordPremiumName() {
            given(pluginMessageEvent.getResult()).willReturn(PluginMessageEvent.ForwardResult.forward());
            given(pluginMessageEvent.getIdentifier()).willReturn(VelocityProxyBridge.AUTHME_CHANNEL);
            given(pluginMessageEvent.getSource()).willReturn(source);
            given(pluginMessageEvent.getData()).willReturn(ProxyTestFixtures.message("premium.set", "Alice"));
            given(source.getServer()).willReturn(authServer);
            bridge.onPluginMessage(pluginMessageEvent);
        }

        private void verifyProfileRequestRewritesToOfflineUuid() {
            GameProfile original = new GameProfile(PREMIUM_UUID, "Alice", List.of());
            GameProfileRequestEvent event = new GameProfileRequestEvent(
                mock(InboundConnection.class), original, true);
            bridge.onGameProfileRequest(event);
            assertEquals(UuidUtils.generateOfflinePlayerUuid("Alice"), event.getGameProfile().getId());
        }
    }
}
