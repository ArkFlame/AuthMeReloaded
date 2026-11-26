package fr.xephi.authme.util.expiring;

import java.util.concurrent.TimeUnit;

/**
 * Base for expiring structures, holding the shared expiration configuration.
 * The expiration is fixed once a structure is created; reconfiguring it only
 * affects entries added afterwards, as documented by the concrete structures.
 */
abstract class AbstractExpiring {

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
}
