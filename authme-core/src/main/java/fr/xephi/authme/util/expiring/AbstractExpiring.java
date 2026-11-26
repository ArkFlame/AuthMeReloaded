package fr.xephi.authme.util.expiring;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Base for expiring structures, holding the shared expiration configuration and the entry storage.
 * The expiration is fixed once a structure is created; reconfiguring it only
 * affects entries added afterwards, as documented by the concrete structures.
 *
 * @param <E> the key/entry type
 * @param <V> the stored value type
 */
abstract class AbstractExpiring<E, V> {

    private final Map<E, V> entries = new ConcurrentHashMap<>();
    private long expirationMillis;

    AbstractExpiring(long duration, TimeUnit unit) {
        this.expirationMillis = unit.toMillis(duration);
    }

    /**
     * Sets a new expiration duration. Note that already present entries
     * will still make use of the old expiration.
     *
     * @param duration the duration of time after which entries expire
     * @param unit the time unit in which {@code duration} is expressed
     */
    public void setExpiration(long duration, TimeUnit unit) {
        this.expirationMillis = unit.toMillis(duration);
    }

    /**
     * Returns the configured expiration duration in milliseconds.
     *
     * @return the expiration in milliseconds
     */
    protected final long getExpirationMillis() {
        return expirationMillis;
    }

    /** @return the internal map */
    protected Map<E, V> getEntries() {
        return entries;
    }

    /** @return true if the structure is really empty, false otherwise */
    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /**
     * Returns whether an entry with the given expiration timestamp has expired at {@code now}.
     * Shared by all expiring structures so the comparison is defined in a single place.
     *
     * @param expiration the expiration timestamp in milliseconds
     * @param now the current time in milliseconds
     * @return true if the entry has expired, false otherwise
     */
    protected static boolean isExpired(long expiration, long now) {
        return now > expiration;
    }
}
