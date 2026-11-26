package fr.xephi.authme.proxy;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.UUID;

/**
 * HMAC signing and secret generation for the proxy-to-backend plugin-message channel.
 * Shared by the Bungee and Velocity bridges; Java 8 compatible so it can live in core.
 */
public final class ProxyMessageSecurity {

    private static final String HMAC_ALGO = "HmacSHA256";
    private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

    private ProxyMessageSecurity() {
    }

    public static String computeHmac(String secret, String playerName, long timestamp, UUID verifiedPremiumUuid) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
            String payload = playerName + ":" + timestamp + ":" + (verifiedPremiumUuid == null ? "" : verifiedPremiumUuid);
            return toHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to compute HMAC-SHA256", e);
        }
    }

    /** Generates a new random secret for the proxy-to-backend HMAC channel. */
    public static String generateSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return toHex(bytes);
    }

    private static String toHex(byte[] bytes) {
        char[] chars = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            chars[i * 2] = HEX_DIGITS[(bytes[i] >> 4) & 0x0f];
            chars[i * 2 + 1] = HEX_DIGITS[bytes[i] & 0x0f];
        }
        return new String(chars);
    }
}
