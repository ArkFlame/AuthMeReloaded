package fr.xephi.authme.output;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.apache.logging.log4j.message.Message;

/**
 * Implements a filter for Log4j to skip sensitive AuthMe commands.
 *
 * @author Xephi59
 */
public class Log4JFilter extends AbstractFilter {

    private static final long serialVersionUID = -5594073755007974254L;

    /**
     * Validates a Message instance and returns the {@link Result} value
     * depending on whether the message contains sensitive AuthMe data.
     *
     * @param message The Message object to verify
     *
     * @return The Result value
     */
    private static Result validateMessage(Message message) {
        return validateMessage(message == null ? null : message.getFormattedMessage());
    }

    /**
     * Validates a message and returns the {@link Result} value depending
     * on whether the message contains sensitive AuthMe data.
     *
     * @param message The message to verify
     *
     * @return The Result value
     */
    private static Result validateMessage(String message) {
        return LogFilterHelper.isSensitiveAuthMeCommand(message)
            ? Result.DENY
            : Result.NEUTRAL;
    }

    @Override
    public Result filter(LogEvent event) {
        return validateMessage(event == null ? null : event.getMessage());
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, Message msg, Throwable t) {
        return validateMessage(msg);
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, String msg, Object... params) {
        return validateMessage(msg);
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, Object msg, Throwable t) {
        return validateMessage(msg == null ? null : msg.toString());
    }
}
