package fr.xephi.authme.bungee;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

final class ProxyMessageSecurity {

    private static final String HMAC_ALGO = "HmacSHA256";

    private ProxyMessageSecurity() {
    }

    static String computeHmac(String secret, String playerName, long timestamp, UUID verifiedPremiumUuid) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
            String payload = playerName + ":" + timestamp + ":" + (verifiedPremiumUuid == null ? "" : verifiedPremiumUuid);
            byte[] hmacBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hmacBytes);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to compute HMAC-SHA256", e);
        }
    }
}
