package fr.xephi.authme.proxy;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

import java.util.UUID;

/**
 * Serializes plugin messages exchanged between a proxy bridge and backend AuthMe servers.
 * Shared by the Bungee and Velocity bridges so the binary wire format stays identical.
 */
public final class ProxyMessages {

    private ProxyMessages() {
    }

    /** Builds the binary {@code perform.login} payload sent to a backend server. */
    public static byte[] performLogin(String typeId, String playerName, long timestamp,
                                      UUID verifiedPremiumUuid, String hmac) {
        ByteArrayDataOutput output = ByteStreams.newDataOutput();
        output.writeUTF(typeId);
        output.writeUTF(playerName);
        output.writeLong(timestamp);
        output.writeUTF(verifiedPremiumUuid == null ? "" : verifiedPremiumUuid.toString());
        output.writeUTF(hmac);
        return output.toByteArray();
    }
}
