package fr.xephi.authme.velocity;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.player.GameProfileRequestEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.InboundConnection;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import com.velocitypowered.api.util.GameProfile;
import com.velocitypowered.api.util.UuidUtils;
import fr.xephi.authme.proxy.ProxyMessageSecurity;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.Logger;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Regression coverage for issue #3126: premium login with {@code keepOfflineUuidCompatibility}
 * must not change the identity the client uses for tamed entities and skin heads.
 */
class Velocity3126Test {

    private static final UUID PREMIUM_UUID = UUID.fromString("8d6d0684-d8b4-4d40-8d2d-0dd4df5555c8");
    private static final UUID OFFLINE_UUID = UuidUtils.generateOfflinePlayerUuid("Alice");
    private static final ServerInfo LOBBY = new ServerInfo("lobby", new InetSocketAddress("127.0.0.1", 25565));
    private static final ServerInfo SURVIVAL = new ServerInfo("survival", new InetSocketAddress("127.0.0.1", 25566));

    /**
     * The proxy-side UUID chain is correct: the backend and the client are given the offline UUID,
     * the premium UUID is kept for authentication and the Mojang skin properties survive the rewrite.
     */
    @Test
    void shouldForwardOfflineUuidToBackendAndKeepPremiumIdentityForVerification() {
        Fixture fixture = new Fixture(true);
        fixture.recordPremiumName();

        GameProfileRequestEvent profileEvent = fixture.verifyProfileRequest();
        GameProfile forwardedProfile = profileEvent.getGameProfile();
        assertEquals(OFFLINE_UUID, forwardedProfile.getId());
        assertEquals(1, forwardedProfile.getProperties().size());
        GameProfile.Property skin = forwardedProfile.getProperties().get(0);
        assertEquals("textures", skin.getName());
        assertEquals("skin-value", skin.getValue());
        assertEquals("skin-signature", skin.getSignature());

        given(fixture.player.getCurrentServer()).willReturn(Optional.of(fixture.currentServer));
        fixture.bridge.onServerConnected(new ServerConnectedEvent(fixture.player, fixture.gameServer, null));

        ArgumentCaptor<byte[]> payload = ArgumentCaptor.forClass(byte[].class);
        verify(fixture.currentServer).sendPluginMessage(eq(VelocityProxyBridge.AUTHME_CHANNEL), payload.capture());
        ByteArrayDataInput input = ByteStreams.newDataInput(payload.getValue());
        assertEquals("perform.login", input.readUTF());
        assertEquals("alice", input.readUTF());
        input.readLong();
        assertEquals(PREMIUM_UUID.toString(), input.readUTF());
        assertEquals(ProxyMessageSecurity.computeHmac("test-secret", "alice",
            ProxyTestFixtures.timestamp(payload.getValue()), PREMIUM_UUID), input.readUTF());
    }

    /** Control case: without offline compatibility the premium UUID is forwarded, so online mode is required. */
    @Test
    void shouldStillForceOnlineModeWhenOfflineUuidCompatibilityIsDisabled() {
        Fixture fixture = new Fixture(false);
        fixture.recordPremiumName();

        PreLoginEvent event = new PreLoginEvent(mock(InboundConnection.class), "Alice", null);
        fixture.bridge.onPreLogin(event);

        assertEquals(PreLoginEvent.PreLoginComponentResult.forceOnlineMode().toString(),
            event.getResult().toString());
    }

    private static final class Fixture {
        private final ProxyServer proxyServer = mock(ProxyServer.class);
        private final Logger logger = mock(Logger.class);
        private final ServerConnection source = mock(ServerConnection.class);
        private final RegisteredServer authServer = mock(RegisteredServer.class);
        private final RegisteredServer gameServer = mock(RegisteredServer.class);
        private final Player player = mock(Player.class);
        private final ServerConnection currentServer = mock(ServerConnection.class);
        private final VelocityProxyBridge bridge;

        private Fixture(boolean keepOfflineUuid) {
            given(authServer.getServerInfo()).willReturn(LOBBY);
            given(gameServer.getServerInfo()).willReturn(SURVIVAL);
            given(player.getUsername()).willReturn("Alice");
            given(currentServer.getServer()).willReturn(gameServer);
            given(currentServer.sendPluginMessage(eq(VelocityProxyBridge.AUTHME_CHANNEL), any(byte[].class)))
                .willReturn(true);
            bridge = new VelocityProxyBridge(proxyServer, logger,
                ProxyTestFixtures.configuration(true, keepOfflineUuid), new VelocityAuthenticationStore(), null);
        }

        private void recordPremiumName() {
            given(source.getServer()).willReturn(authServer);
            bridge.onPluginMessage(new PluginMessageEvent(source, source, VelocityProxyBridge.AUTHME_CHANNEL,
                ProxyTestFixtures.message("premium.set", "Alice")));
        }

        private GameProfileRequestEvent verifyProfileRequest() {
            GameProfile original = new GameProfile(PREMIUM_UUID, "Alice",
                List.of(new GameProfile.Property("textures", "skin-value", "skin-signature")));
            GameProfileRequestEvent event = new GameProfileRequestEvent(
                mock(InboundConnection.class), original, true);
            bridge.onGameProfileRequest(event);
            return event;
        }
    }
}
