package fr.xephi.authme.util.expiring;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * Test for {@link Duration}.
 */
class DurationTest {

    @ParameterizedTest
    @CsvSource({
        "0, HOURS, 0, SECONDS",
        "124, MINUTES, 2, HOURS",
        "300, HOURS, 12, DAYS",
        "72008, MINUTES, 50, DAYS",
        "604803000, MILLISECONDS, 7, DAYS",
        "10801400, MILLISECONDS, 3, HOURS",
        "248, SECONDS, 4, MINUTES"
    })
    void shouldConvertToAppropriateTimeUnit(long sourceDuration, TimeUnit sourceUnit,
                                            long expectedDuration, TimeUnit expectedUnit) {
        check(Duration.createWithSuitableUnit(sourceDuration, sourceUnit), expectedDuration, expectedUnit);
    }

    private static void check(Duration duration, long expectedDuration, TimeUnit expectedUnit) {
        assertThat(duration.getTimeUnit(), equalTo(expectedUnit));
        assertThat(duration.getDuration(), equalTo(expectedDuration));
    }
}
