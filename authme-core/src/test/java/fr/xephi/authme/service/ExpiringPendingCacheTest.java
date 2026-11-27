package fr.xephi.authme.service;

import org.junit.jupiter.api.Test;

import java.util.Collection;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

/**
 * Test for {@link ExpiringPendingCache}. A huge TTL means "never expires" and a negative one
 * "already expired", so no test depends on the clock.
 */
class ExpiringPendingCacheTest {

    private static final long VALID = 3_600_000L;
    private static final long EXPIRED = -1_000L;

    @Test
    void shouldMatchNamesCaseInsensitively() {
        ExpiringPendingCache<String> cache = new ExpiringPendingCache<>(VALID);

        cache.put("Bobby", "code");

        assertThat(cache.get("bOBBY"), equalTo("code"));
        assertThat(cache.remove("BOBBY"), equalTo("code"));
        assertThat(cache.get("bobby"), nullValue());
    }

    @Test
    void shouldNotReturnExpiredValues() {
        ExpiringPendingCache<String> cache = new ExpiringPendingCache<>(EXPIRED);

        cache.put("bobby", "code");

        assertThat(cache.get("bobby"), nullValue());
        cache.put("bobby", "code");
        assertThat(cache.remove("bobby"), nullValue());
    }

    @Test
    void shouldEvictOnlyExpiredEntries() {
        ExpiringPendingCache<String> expired = new ExpiringPendingCache<>(EXPIRED);
        expired.put("Old", "x");
        ExpiringPendingCache<String> valid = new ExpiringPendingCache<>(VALID);
        valid.put("new", "y");

        Collection<String> evicted = expired.evictExpired();

        assertThat(evicted, contains("old"));
        assertThat(expired.evictExpired(), empty());
        assertThat(valid.evictExpired(), empty());
        assertThat(valid.get("new"), equalTo("y"));
    }
}
