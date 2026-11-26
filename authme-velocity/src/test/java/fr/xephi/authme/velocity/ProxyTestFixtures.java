package fr.xephi.authme.velocity;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

import java.util.Set;

/** Helpers shared by the velocity proxy tests. */
final class ProxyTestFixtures {

    private ProxyTestFixtures() {
    }

    static byte[] message(String typeId, String playerName) {
        ByteArrayDataOutput output = ByteStreams.newDataOutput();
        output.writeUTF(typeId);
        output.writeUTF(playerName);
        return output.toByteArray();
    }

    static VelocityProxyConfiguration configuration(boolean autoLogin, boolean keepOfflineUuid) {
        return configuration(autoLogin, keepOfflineUuid, Set.of("/login"));
    }

    static VelocityProxyConfiguration configuration(boolean autoLogin, boolean keepOfflineUuid, Set<String> commands) {
        return new VelocityProxyConfiguration(Set.of("lobby"), false, true,
            "Authentication required.", autoLogin, false, "", true,
            commands, true, "", "test-secret", keepOfflineUuid);
    }

    static long timestamp(byte[] payload) {
        ByteArrayDataInput input = ByteStreams.newDataInput(payload);
        input.readUTF();
        input.readUTF();
        return input.readLong();
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
