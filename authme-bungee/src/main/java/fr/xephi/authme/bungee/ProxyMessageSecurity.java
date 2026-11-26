package fr.xephi.authme.bungee;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.UUID;

final class ProxyMessageSecurity {

    private static final String HMAC_ALGO = "HmacSHA256";
    private static final HexFormat HEX = HexFormat.of();

    private ProxyMessageSecurity() {
    }

    static String computeHmac(String secret, String playerName, long timestamp, UUID verifiedPremiumUuid) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
            String payload = playerName + ":" + timestamp + ":" + (verifiedPremiumUuid == null ? "" : verifiedPremiumUuid);
            byte[] hmacBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HEX.formatHex(hmacBytes);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to compute HMAC-SHA256", e);
        }
    }

    /** Generates a new random secret for the proxy-to-backend HMAC channel. */
    static String generateSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return HEX.formatHex(bytes);
    }
}
