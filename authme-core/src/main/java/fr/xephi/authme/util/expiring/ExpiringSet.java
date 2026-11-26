package fr.xephi.authme.util.expiring;

import java.util.concurrent.TimeUnit;

/**
 * Set whose entries expire after a configurable amount of time; an expired entry behaves as if
 * absent. Expired entries are cleared lazily on access or via {@link #removeExpiredEntries()}.
 * Adding an entry that is already present resets its expiration.
 *
 * @param <E> the type of the entries
 */
public class ExpiringSet<E> extends AbstractExpiring<E, Long> {

    /**
     * Constructor.
     *
     * @param duration the duration of time after which entries expire
     * @param unit the time unit in which {@code duration} is expressed
     */
    public ExpiringSet(long duration, TimeUnit unit) {
        super(duration, unit);
    }

    /** Adds an entry to the set, resetting the expiration if it is already present. */
    public void add(E entry) {
        getEntries().put(entry, System.currentTimeMillis() + getExpirationMillis());
    }

    /** Returns whether this set contains the given entry, if it hasn't expired. */
    public boolean contains(E entry) {
        Long expiration = getEntries().get(entry);
        if (expiration != null && expiration <= System.currentTimeMillis()) {
            getEntries().remove(entry);
            expiration = null;
        }
        return expiration != null;
    }

    /** Removes the given entry from the set (if present). */
    public void remove(E entry) {
        getEntries().remove(entry);
    }

    /** Removes all entries from the set. */
    public void clear() {
        getEntries().clear();
    }

    /** Removes all entries which have expired from the internal structure. */
    public void removeExpiredEntries() {
        long now = System.currentTimeMillis();
        getEntries().entrySet().removeIf(entry -> isExpired(entry.getValue(), now));
    }

    /**
     * Returns the duration of the entry until it expires (provided it is not removed or re-added).
     * If the entry does not exist, a duration of -1 seconds is returned.
     *
     * @param entry the entry whose duration before it expires should be returned
     * @return duration the entry will remain in the set (if there are not modifications)
     */
    public Duration getExpiration(E entry) {
        Long expiration = getEntries().get(entry);
        if (expiration != null) {
            long stillPresentMillis = expiration - System.currentTimeMillis();
            if (stillPresentMillis >= 0) {
                return Duration.createWithSuitableUnit(stillPresentMillis, TimeUnit.MILLISECONDS);
            }
            getEntries().remove(entry);
        }
        return new Duration(-1, TimeUnit.SECONDS);
    }
}
