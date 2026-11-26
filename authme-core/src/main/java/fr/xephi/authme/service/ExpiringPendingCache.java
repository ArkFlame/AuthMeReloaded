package fr.xephi.authme.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared storage for short-lived, per-player state that waits on an external confirmation
 * (an emailed code or a Mojang session check). Player names are matched case-insensitively.
 *
 * <p>Expired entries are discarded lazily on access and by explicit eviction; there is no
 * background thread. Keeping this logic in one place lets the pending caches share the same
 * concurrency and expiry semantics without duplicating their bookkeeping.</p>
 *
 * @param <V> the type of the cached value
 */
final class ExpiringPendingCache<V> {

    private final ConcurrentHashMap<String, ExpiringValue<V>> entries = new ConcurrentHashMap<>();
    private final long ttlMillis;

    ExpiringPendingCache(long ttlMillis) {
        this.ttlMillis = ttlMillis;
    }

    /**
     * Stores a value for the given player, replacing and re-timing any previous value.
     *
     * @param name the player name (case-insensitive)
     * @param value the value to cache
     */
    void put(String name, V value) {
        entries.put(key(name), new ExpiringValue<>(value, System.currentTimeMillis() + ttlMillis));
    }

    /**
     * Returns the value for the given player if it exists and has not expired. An expired
     * entry is removed as part of the lookup.
     *
     * @param name the player name (case-insensitive)
     * @return the cached value, or {@code null} if absent or expired
     */
    V get(String name) {
        String key = key(name);
        ExpiringValue<V> value = entries.get(key);
        if (value == null) {
            return null;
        }
        if (System.currentTimeMillis() > value.expiresAt) {
            entries.remove(key, value);
            return null;
        }
        return value.value;
    }

    /**
     * Removes and returns the value for the given player, or {@code null} if it was absent
     * or had already expired.
     *
     * @param name the player name (case-insensitive)
     * @return the cached value if it was still valid, or {@code null}
     */
    V remove(String name) {
        ExpiringValue<V> value = entries.remove(key(name));
        if (value == null || System.currentTimeMillis() > value.expiresAt) {
            return null;
        }
        return value.value;
    }

    /**
     * Removes all expired entries, returning the names of the ones that were evicted.
     *
     * @return the lowercase names of evicted entries
     */
    Collection<String> evictExpired() {
        long now = System.currentTimeMillis();
        Collection<String> evicted = new ArrayList<>();
        entries.entrySet().removeIf(entry -> {
            if (now > entry.getValue().expiresAt) {
                evicted.add(entry.getKey());
                return true;
            }
            return false;
        });
        return evicted.isEmpty() ? Collections.emptyList() : evicted;
    }

    /**
     * Removes all expired entries without collecting their names. Prefer this over
     * {@link #evictExpired()} when the evicted names are not needed.
     */
    void purgeExpired() {
        long now = System.currentTimeMillis();
        entries.entrySet().removeIf(entry -> now > entry.getValue().expiresAt);
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private static final class ExpiringValue<V> {

        private final V value;
        private final long expiresAt;

        ExpiringValue(V value, long expiresAt) {
            this.value = value;
            this.expiresAt = expiresAt;
        }
    }
}
