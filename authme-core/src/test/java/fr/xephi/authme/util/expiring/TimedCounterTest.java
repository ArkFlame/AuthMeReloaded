package fr.xephi.authme.util.expiring;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * Test for {@link TimedCounter}.
 */
class TimedCounterTest {

    @Test
    void shouldReturnZeroForAnyKey() {
        TimedCounter<Double> counter = new TimedCounter<>(1, TimeUnit.DAYS);

        assertThat(counter.get(2.0), equalTo(0));
        assertThat(counter.get(-3.14159), equalTo(0));
    }

    @Test
    void shouldIncrementAndDecrementCount() {
        TimedCounter<String> increment = new TimedCounter<>(10, TimeUnit.MINUTES);
        increment.put("moto", 12);
        increment.increment("hello");
        increment.increment("moto");
        assertThat(increment.get("hello"), equalTo(1));
        assertThat(increment.get("moto"), equalTo(13));

        TimedCounter<String> decrement = new TimedCounter<>(10, TimeUnit.MINUTES);
        decrement.put("moto", 12);
        decrement.decrement("hello");
        decrement.decrement("moto");
        assertThat(decrement.get("hello"), equalTo(0));
        assertThat(decrement.get("moto"), equalTo(11));
    }

    @Test
    void shouldSumUpEntries() {
        TimedCounter<String> counter = new TimedCounter<>(90, TimeUnit.SECONDS);
        counter.getEntries().put("expired", new ExpiringMap.ExpiringEntry<>(800, 0));
        counter.getEntries().put("expired2", new ExpiringMap.ExpiringEntry<>(24, System.currentTimeMillis() - 100));
        counter.put("other", 10);
        counter.put("Another", 4);

        int totals = counter.total();

        assertThat(totals, equalTo(14));
    }
}
