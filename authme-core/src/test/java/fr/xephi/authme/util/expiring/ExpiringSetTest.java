package fr.xephi.authme.util.expiring;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * Test for {@link ExpiringSet}.
 */
class ExpiringSetTest {

    @Test
    void shouldAddEntry() {
        ExpiringSet<String> set = new ExpiringSet<>(10, TimeUnit.MINUTES);

        set.add("authme");

        assertThat(set.contains("authme"), equalTo(true));
        assertThat(set.contains("other"), equalTo(false));
    }

    @Test
    void shouldRemoveEntries() {
        ExpiringSet<Integer> set = new ExpiringSet<>(20, TimeUnit.SECONDS);
        set.add(20);
        set.add(40);

        set.remove(40);
        set.remove(60);

        assertThat(set.contains(20), equalTo(true));
        assertThat(set.contains(40), equalTo(false));
        assertThat(set.contains(60), equalTo(false));
    }

    @Test
    void shouldHandleNewExpirationAndSupportNegativeValues() {
        ExpiringSet<Character> set = new ExpiringSet<>(800, TimeUnit.MILLISECONDS);
        set.add('A');

        set.setExpiration(-10, TimeUnit.SECONDS);
        set.add('Y');

        assertThat(set.contains('A'), equalTo(true));
        assertThat(set.contains('Y'), equalTo(false));
    }

    @Test
    void shouldClearAllValues() {
        ExpiringSet<String> set = new ExpiringSet<>(1, TimeUnit.MINUTES);
        set.add("test");

        assertThat(set.isEmpty(), equalTo(false));
        set.clear();
        assertThat(set.isEmpty(), equalTo(true));
        assertThat(set.contains("test"), equalTo(false));
    }

    @Test
    void shouldClearExpiredValues() {
        ExpiringSet<Integer> set = new ExpiringSet<>(2, TimeUnit.HOURS);
        set.add(2);
        set.setExpiration(-100, TimeUnit.SECONDS);
        set.add(3);
        set.setExpiration(20, TimeUnit.MINUTES);
        set.add(6);

        set.removeExpiredEntries();

        assertThat(set.contains(2), equalTo(true));
        assertThat(set.contains(3), equalTo(false));
        assertThat(set.contains(6), equalTo(true));
    }

    @Test
    void shouldReturnExpiration() {
        ExpiringSet<String> set = new ExpiringSet<>(123, TimeUnit.MINUTES);
        set.add("my entry");

        Duration expiration = set.getExpiration("my entry");
        Duration unknownExpiration = set.getExpiration("bogus");

        assertIsDuration(expiration, 2, TimeUnit.HOURS);
        assertIsDuration(unknownExpiration, -1, TimeUnit.SECONDS);
    }

    @Test
    void shouldReturnExpirationInSuitableUnits() {
        ExpiringSet<Integer> set = new ExpiringSet<>(601, TimeUnit.SECONDS);
        set.add(12);
        set.setExpiration(49, TimeUnit.HOURS);
        set.add(23);

        Duration expiration12 = set.getExpiration(12);
        Duration expiration23 = set.getExpiration(23);
        Duration expectedUnknown = set.getExpiration(-100);

        assertIsDuration(expiration12, 10, TimeUnit.MINUTES);
        assertIsDuration(expiration23, 2, TimeUnit.DAYS);
        assertIsDuration(expectedUnknown, -1, TimeUnit.SECONDS);
    }

    @Test
    void shouldReturnMinusOneForExpiredEntry() {
        ExpiringSet<Integer> set = new ExpiringSet<>(-100, TimeUnit.SECONDS);
        set.add(23);

        Duration expiration = set.getExpiration(23);

        assertIsDuration(expiration, -1, TimeUnit.SECONDS);
    }

    private static void assertIsDuration(Duration duration, long expectedDuration, TimeUnit expectedUnit) {
        assertThat(duration.getTimeUnit(), equalTo(expectedUnit));
        assertThat(duration.getDuration(), equalTo(expectedDuration));
    }
}
