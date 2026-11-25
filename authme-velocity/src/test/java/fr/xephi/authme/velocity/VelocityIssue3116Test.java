package fr.xephi.authme.velocity;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.ChannelRegistrar;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.Logger;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

class VelocityIssue3116Test {

    @Test
    void shouldRetryStartupHandshakeWithoutWaitingForFirstPlayerConnection() {
        ProxyServer proxyServer = mock(ProxyServer.class);
        Logger logger = mock(Logger.class);
        ChannelRegistrar registrar = mock(ChannelRegistrar.class);
        RegisteredServer authServer = mock(RegisteredServer.class);
        ServerInfo serverInfo = mock(ServerInfo.class);

        given(proxyServer.getChannelRegistrar()).willReturn(registrar);
        given(proxyServer.getAllServers()).willReturn(List.of(authServer));
        given(authServer.getServerInfo()).willReturn(serverInfo);
        given(serverInfo.getName()).willReturn("lobby");
        // No player is connected to the backend: the first send fails, a retry must still happen.
        given(authServer.getPlayersConnected()).willReturn(List.of());
        given(authServer.sendPluginMessage(eq(VelocityProxyBridge.AUTHME_CHANNEL), any(byte[].class)))
            .willReturn(false, true);

        VelocityProxyBridge bridge = new VelocityProxyBridge(proxyServer, logger,
            createConfiguration(), new VelocityAuthenticationStore(), null);
        try {
            bridge.registerChannels();

            ArgumentCaptor<byte[]> payloads = ArgumentCaptor.forClass(byte[].class);
            verify(authServer, timeout(4000).times(2))
                .sendPluginMessage(eq(VelocityProxyBridge.AUTHME_CHANNEL), payloads.capture());
            ByteArrayDataInput input = ByteStreams.newDataInput(payloads.getValue());
            assertEquals("proxy.started", input.readUTF());
            assertEquals("velocity", input.readUTF());
        } finally {
            bridge.shutdown();
        }
    }

    private static VelocityProxyConfiguration createConfiguration() {
        return new VelocityProxyConfiguration(Set.of("lobby"), false, true,
            "Authentication required.", true, false, "", true,
            Set.of("/login"), true, "", "test-secret", false);
    }
}
