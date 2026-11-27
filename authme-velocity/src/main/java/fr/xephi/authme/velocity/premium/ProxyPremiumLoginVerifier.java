package fr.xephi.authme.velocity.premium;

import fr.xephi.authme.velocity.VelocityAuthenticationStore;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * Short-lived mapping of player names to the Mojang UUIDs verified by Velocity, kept only
 * until {@code perform.login} is sent to a backend. Velocity resolves the premium session
 * natively, so no local encryption/hasJoined handshake is needed.
 */
final class ProxyPremiumLoginVerifier {

    private static final long VERIFIED_TTL_MS = 120_000L;

    private final Consumer<String> warningLogger;
    private final ConcurrentHashMap<String, VerifiedSession> verified = new ConcurrentHashMap<>();

    private final LongSupplier clock;

    ProxyPremiumLoginVerifier(Consumer<String> warningLogger) {
        this(warningLogger, System::currentTimeMillis);
    }

    ProxyPremiumLoginVerifier(Consumer<String> warningLogger, LongSupplier clock) {
        this.warningLogger = warningLogger;
        this.clock = clock;
    }

    void storeVerified(String username, UUID mojangUuid) {
        long now = clock.getAsLong();
        // A verified player who disconnects before perform.login is sent is never looked up again: sweep them here.
        verified.values().removeIf(session -> now - session.verifiedAt() > VERIFIED_TTL_MS);
        verified.put(VelocityAuthenticationStore.normalizeName(username), new VerifiedSession(mojangUuid, now));
    }

    UUID getVerifiedUuid(String username) {
        String normalizedName = VelocityAuthenticationStore.normalizeName(username);
        VerifiedSession verifiedSession = verified.get(normalizedName);
        if (verifiedSession == null) {
            return null;
        }
        if (clock.getAsLong() - verifiedSession.verifiedAt() > VERIFIED_TTL_MS) {
            warningLogger.accept("Verified premium session for '" + normalizedName + "' expired (TTL "
                + VERIFIED_TTL_MS + "ms) before perform.login could use it");
            verified.remove(normalizedName);
            return null;
        }
        return verifiedSession.mojangUuid();
    }

    void clearVerified(String username) {
        verified.remove(VelocityAuthenticationStore.normalizeName(username));
    }

    void shutdown() {
        verified.clear();
    }

    private record VerifiedSession(UUID mojangUuid, long verifiedAt) { }
}
