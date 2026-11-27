package fr.xephi.authme.service;

import javax.inject.Inject;
import java.util.Objects;

/**
 * Holds in-memory email confirmation requests that have not yet been verified by a code sent
 * to the new address. Entries expire after {@link #TTL_MS} (10 minutes).
 *
 * <p>A player is placed here when they run {@code /email add} or {@code /email change}.
 * Instead of saving the address immediately, a verification code is emailed to the new address.
 * The entry is consumed (and the address persisted) when the player submits the correct code
 * via {@code /email confirm <code>}. Expired entries are silently discarded.</p>
 */
public class PendingEmailVerificationCache {

    static final long TTL_MS = 10 * 60 * 1000L;

    private final ExpiringPendingCache<PendingEntry> pending = new ExpiringPendingCache<>(TTL_MS);

    @Inject
    PendingEmailVerificationCache() {
    }

    /**
     * Registers a pending email confirmation for the given player. Any previous entry is replaced.
     * Also evicts expired entries.
     *
     * @param name  the player name (case-insensitive)
     * @param email the new email address awaiting confirmation
     * @param code  the verification code sent to that address
     */
    public void addPending(String name, String email, String code) {
        pending.evictExpired();
        pending.put(name, new PendingEntry(email, code));
    }

    /**
     * Returns the pending entry for the given player name without removing it,
     * or {@code null} if no entry exists or it has expired.
     *
     * @param name the player name (case-insensitive)
     * @return the pending entry, or null
     */
    public PendingEntry getEntry(String name) {
        return pending.get(name);
    }

    /**
     * Removes and returns the pending entry for the given player name,
     * or {@code null} if no entry exists or it has expired.
     *
     * @param name the player name (case-insensitive)
     * @return the pending entry if valid, or null
     */
    public PendingEntry removePending(String name) {
        return pending.remove(name);
    }

    /**
     * Value awaiting confirmation. Expiry is managed by {@link ExpiringPendingCache}.
     */
    public static final class PendingEntry {
        private final String email;
        private final String code;

        public PendingEntry(String email, String code) {
            this.email = Objects.requireNonNull(email, "email");
            this.code = Objects.requireNonNull(code, "code");
        }

        public String email() {
            return email;
        }

        public String code() {
            return code;
        }
    }
}
