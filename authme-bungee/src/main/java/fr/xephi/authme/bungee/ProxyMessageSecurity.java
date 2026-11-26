package fr.xephi.authme.bungee;

import java.util.UUID;

/** Facade kept for callers and tests; implementation in {@code fr.xephi.authme.proxy.ProxyMessageSecurity}. */
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
