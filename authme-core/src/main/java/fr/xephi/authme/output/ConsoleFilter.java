package fr.xephi.authme.output;

import java.util.logging.Filter;
import java.util.logging.LogRecord;

/**
 * Console filter to replace sensitive AuthMe commands with a generic message.
 *
 * @author Xephi59
 */
public class ConsoleFilter implements Filter {

    @Override
    public boolean isLoggable(LogRecord record) {
        if (record == null) {
            return true;
        }
        String message = record.getMessage();
        if (LogFilterHelper.isSensitiveAuthMeCommand(message)) {
            String playerName = message.split(" ")[0];
            record.setMessage(playerName + " issued an AuthMe command");
        }
        return true;
    }

}
