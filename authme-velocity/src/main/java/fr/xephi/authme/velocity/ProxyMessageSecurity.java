package fr.xephi.authme.velocity;

import java.util.UUID;

/**
 * Module-local facade kept for callers and tests that use this name; the implementation lives in
 * {@code fr.xephi.authme.proxy.ProxyMessageSecurity} and is shared with the Bungee bridge.
 */
final class ProxyMessageSecurity {

    private ProxyMessageSecurity() {
    }

    static String computeHmac(String secret, String playerName, long timestamp, UUID verifiedPremiumUuid) {
        return fr.xephi.authme.proxy.ProxyMessageSecurity.computeHmac(
            secret, playerName, timestamp, verifiedPremiumUuid);
    }

    static String generateSecret() {
        return fr.xephi.authme.proxy.ProxyMessageSecurity.generateSecret();
    }
}
