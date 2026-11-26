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
     * Validates a log message and returns the {@link Result} value depending on whether
     * it contains sensitive AuthMe data. Log4j hands the text over as a {@link Message},
     * a plain String or an arbitrary object, so all filter methods funnel through here.
     *
     * @param message the message to verify, may be null
     * @return the Result value
     */
    private static Result validateMessage(Object message) {
        String text = message instanceof Message ? ((Message) message).getFormattedMessage()
            : message == null ? null : message.toString();
        return LogFilterHelper.isSensitiveAuthMeCommand(text) ? Result.DENY : Result.NEUTRAL;
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
