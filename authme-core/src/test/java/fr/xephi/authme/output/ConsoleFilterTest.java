package fr.xephi.authme.output;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.logging.LogRecord;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test for {@link ConsoleFilter}.
 */
class ConsoleFilterTest {

    private final ConsoleFilter filter = new ConsoleFilter();

    private static final String SENSITIVE_COMMAND = "User issued server command: /login test test";
    private static final String NORMAL_COMMAND = "User issued server command: /motd 2";

    private static Stream<Arguments> recordsAndExpectedFiltering() {
        return Stream.of(
            Arguments.of(SENSITIVE_COMMAND, true),
            Arguments.of(NORMAL_COMMAND, false),
            Arguments.of((String) null, false));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("recordsAndExpectedFiltering")
    void shouldFilterRecordsAsExpected(String message, boolean shouldFilter) {
        LogRecord record = mock(LogRecord.class);
        when(record.getMessage()).thenReturn(message);

        boolean result = filter.isLoggable(record);

        assertThat(result, equalTo(true));
        if (shouldFilter) {
            verify(record).setMessage("User issued an AuthMe command");
        } else {
            verify(record, never()).setMessage("User issued an AuthMe command");
        }
    }
}
