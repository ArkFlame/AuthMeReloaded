package fr.xephi.authme.output;

import org.apache.logging.log4j.core.Filter.Result;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.message.Message;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.when;

/**
 * Test for {@link Log4JFilter}.
 */
class Log4JFilterTest {

    private final Log4JFilter log4JFilter = new Log4JFilter();

    private static final String SENSITIVE_COMMAND = "User issued server command: /login pass pass";
    private static final String NORMAL_COMMAND = "User issued server command: /help";
    private static final String OTHER_COMMAND = "Starting the server... Write /l for logs";

    // ---------
    // Test the filter(LogEvent) method
    // ---------
    @Test
    void shouldFilterSensitiveLogEvent() {
        // given
        // when
        Result result = filterLogEvent(SENSITIVE_COMMAND);

        // then
        assertThat(result, equalTo(Result.DENY));
    }

    @Test
    void shouldNotFilterIrrelevantLogEvent() {
        // given
        // when
        Result result = filterLogEvent(NORMAL_COMMAND);

        // then
        assertThat(result, equalTo(Result.NEUTRAL));
    }

    @Test
    void shouldNotFilterNonCommandLogEvent() {
        // given
        // when
        Result result = filterLogEvent(OTHER_COMMAND);

        // then
        assertThat(result, equalTo(Result.NEUTRAL));
    }

    @Test
    void shouldNotFilterLogEventWithNullMessage() {
        // given
        // when
        Result result = filterLogEvent(null);

        // then
        assertThat(result, equalTo(Result.NEUTRAL));
    }

    @Test
    void shouldNotFilterWhenLogEventIsNull() {
        // given / when
        Result result = log4JFilter.filter(null);

        // then
        assertThat(result, equalTo(Result.NEUTRAL));
    }

    // ----------
    // Test filter(Logger, Level, Marker, String, Object...)
    // ----------
    @Test
    void shouldFilterSensitiveStringMessage() {
        // given / when
        Result result = log4JFilter.filter(null, null, null, SENSITIVE_COMMAND);

        // then
        assertThat(result, equalTo(Result.DENY));
    }

    @Test
    void shouldNotFilterNormalStringMessage() {
        // given / when
        Result result = log4JFilter.filter(null, null, null, NORMAL_COMMAND);

        // then
        assertThat(result, equalTo(Result.NEUTRAL));
    }

    @Test
    void shouldNotFilterNonCommandStringMessage() {
        // given / when
        Result result = log4JFilter.filter(null, null, null, OTHER_COMMAND);

        // then
        assertThat(result, equalTo(Result.NEUTRAL));
    }

    @Test
    void shouldReturnNeutralForNullMessage() {
        // given / when
        Result result = log4JFilter.filter(null, null, null, null);

        // then
        assertThat(result, equalTo(Result.NEUTRAL));
    }

    // --------
    // Test filter(Logger, Level, Marker, Object, Throwable)
    // --------
    @Test
    void shouldFilterSensitiveObjectMessage() {
        // given / when
        Result result = log4JFilter.filter(null, null, null, (Object) SENSITIVE_COMMAND, new Exception());

        // then
        assertThat(result, equalTo(Result.DENY));
    }

    @Test
    void shouldNotFilterNullObjectParam() {
        // given / when
        Result result = log4JFilter.filter(null, null, null, (Object) null, new Exception());

        // then
        assertThat(result, equalTo(Result.NEUTRAL));
    }

    @Test
    void shouldNotFilterIrrelevantMessage() {
        // given / when
        Result result = log4JFilter.filter(null, null, null, (Object) OTHER_COMMAND, new Exception());

        // then
        assertThat(result, equalTo(Result.NEUTRAL));
    }

    @Test
    void shouldNotFilterNonSensitiveCommand() {
        // given / when
        Result result = log4JFilter.filter(null, null, null, (Object) NORMAL_COMMAND, new Exception());

        // then
        assertThat(result, equalTo(Result.NEUTRAL));
    }

    // --------
    // Test filter(Logger, Level, Marker, Message, Throwable)
    // --------
    @Test
    void shouldFilterSensitiveMessage() {
        // given
        Message message = mockMessage(SENSITIVE_COMMAND);

        // when
        Result result = log4JFilter.filter(null, null, null, message, new Exception());

        // then
        assertThat(result, equalTo(Result.DENY));
    }

    @Test
    void shouldNotFilterNonSensitiveMessage() {
        // given
        Message message = mockMessage(NORMAL_COMMAND);

        // when
        Result result = log4JFilter.filter(null, null, null, message, new Exception());

        // then
        assertThat(result, equalTo(Result.NEUTRAL));
    }

    @Test
    void shouldNotFilterNonCommandMessage() {
        // given
        Message message = mockMessage(OTHER_COMMAND);

        // when
        Result result = log4JFilter.filter(null, null, null, message, new Exception());

        // then
        assertThat(result, equalTo(Result.NEUTRAL));
    }

    @Test
    void shouldNotFilterNullMessage() {
        // given / when
        Result result = log4JFilter.filter(null, null, null, (Object) null, new Exception());

        // then
        assertThat(result, equalTo(Result.NEUTRAL));
    }

    private Result filterLogEvent(String formattedMessage) {
        Message message = mockMessage(formattedMessage);
        LogEvent event = Mockito.mock(LogEvent.class);
        when(event.getMessage()).thenReturn(message);
        return log4JFilter.filter(event);
    }

    /**
     * Mocks a {@link Message} object and makes it return the given formatted message.
     *
     * @param formattedMessage the formatted message the mock should return
     * @return Message mock
     */
    private static Message mockMessage(String formattedMessage) {
        Message message = Mockito.mock(Message.class);
        when(message.getFormattedMessage()).thenReturn(formattedMessage);
        return message;
    }

}
