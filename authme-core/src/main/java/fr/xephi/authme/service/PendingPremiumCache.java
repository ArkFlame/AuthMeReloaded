package fr.xephi.authme.service;

import javax.inject.Inject;
import java.util.Collection;
import java.util.UUID;

/**
 * Holds in-memory premium verification requests that have not yet been confirmed by a
 * cryptographic Mojang session check. Entries expire after {@link #TTL_MS} (5 minutes).
 *
 * <p>A player is placed here when they run {@code /premium} (or an admin runs
 * {@code /authme premium}) on an offline-mode backend. The entry is consumed (and the
 * UUID persisted to the database) when the player reconnects and the Mojang session check
 * succeeds. If the check fails, the entry is removed without saving.</p>
 */
public class PendingPremiumCache {

    static final long TTL_MS = 5 * 60 * 1000L;

    private final ExpiringPendingCache<UUID> pending = new ExpiringPendingCache<>(TTL_MS);

    @Inject
    PendingPremiumCache() {
    }

    /**
     * Registers a pending premium verification for the given player name.
     * Also evicts any already-expired entries.
     *
     * @param name       the player name (case-insensitive)
     * @param mojangUuid the Mojang UUID fetched from the profile API
     * @return the names of any entries that were evicted due to expiry
     */
    public Collection<String> addPending(String name, UUID mojangUuid) {
        Collection<String> evicted = pending.evictExpired();
        pending.put(name, mojangUuid);
        return evicted;
    }

    /**
     * Returns whether a non-expired pending entry exists for the given player name.
     *
     * @param name the player name (case-insensitive)
     * @return true if a pending entry exists and has not yet expired
     */
    public boolean isPending(String name) {
        return pending.get(name) != null;
    }

    /**
     * Returns the Mojang UUID associated with the pending entry for the given player name,
     * or {@code null} if no entry exists or it has expired.
     *
     * @param name the player name (case-insensitive)
     * @return the pending Mojang UUID, or null
     */
    public UUID getPendingUuid(String name) {
        return pending.get(name);
    }

    /**
     * Removes and returns the pending Mojang UUID for the given player name.
     *
     * @param name the player name (case-insensitive)
     * @return the pending Mojang UUID if a valid entry existed, or null
     */
    public UUID removePending(String name) {
        return pending.remove(name);
    }

    /**
     * Removes all expired entries and returns their player names so the caller can
     * notify the proxy (via {@code PREMIUM_UNSET}) that the pending state is gone.
     *
     * @return the names of evicted entries (lowercase)
     */
    public Collection<String> evictExpired() {
        return pending.evictExpired();
    }
}
