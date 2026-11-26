package fr.xephi.authme.bungee;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

import java.util.Set;

/** Helpers shared by the bungee proxy tests. */
final class ProxyTestFixtures {

    private ProxyTestFixtures() {
    }

    static byte[] message(String typeId, String playerName) {
        ByteArrayDataOutput output = ByteStreams.newDataOutput();
        output.writeUTF(typeId);
        output.writeUTF(playerName);
        return output.toByteArray();
    }

    static BungeeProxyConfiguration configuration() {
        return new BungeeProxyConfiguration(Set.of("lobby"), false, true,
            Set.of("/login", "/register", "/l", "/reg", "/email", "/captcha", "/2fa", "/totp", "/log"),
            true, true, "Authentication required.", true, false, "", "", "test-secret", false);
    }

    static byte[] chunkPayload(int seq, boolean last, String csv) {
        ByteArrayDataOutput output = ByteStreams.newDataOutput();
        output.writeUTF("premium.list.chunk");
        output.writeUTF(seq + ":" + (last ? "1" : "0") + ":" + csv);
        return output.toByteArray();
    }

    static byte[] listPayload(String csv) {
        ByteArrayDataOutput output = ByteStreams.newDataOutput();
        output.writeUTF("premium.list");
        output.writeUTF(csv);
        return output.toByteArray();
    }
}

