package fr.xephi.authme.util.expiring;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Base for expiring structures, holding the expiration configuration and the entry storage.
 * Reconfiguring the expiration only affects entries added afterwards.
 *
 * @param <E> the key/entry type
 * @param <V> the stored value type
 */
abstract class AbstractExpiring<E, V> {

    private final Map<E, V> entries = new ConcurrentHashMap<>();
    private volatile long expirationMillis;

    AbstractExpiring(long duration, TimeUnit unit) {
        this.expirationMillis = unit.toMillis(duration);
    }

    /**
     * Sets a new expiration duration; already present entries keep the old one.
     *
     * @param duration the duration after which entries expire
     * @param unit the time unit in which {@code duration} is expressed
     */
    public void setExpiration(long duration, TimeUnit unit) {
        this.expirationMillis = unit.toMillis(duration);
    }

    /** @return the configured expiration duration in milliseconds */
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
     * Returns whether an entry expiring at {@code expiration} has expired at {@code now}.
     *
     * @param expiration the expiration timestamp in milliseconds
     * @param now the current time in milliseconds
     * @return true if the entry has expired, false otherwise
     */
    protected static boolean isExpired(long expiration, long now) {
        return now > expiration;
    }
}
