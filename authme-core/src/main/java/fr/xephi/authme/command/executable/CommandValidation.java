package fr.xephi.authme.command.executable;

import fr.xephi.authme.service.CommonService;
import fr.xephi.authme.service.ValidationService.ValidationResult;
import org.bukkit.command.CommandSender;

/**
 * Common handling of a {@link ValidationResult} in commands that validate user input.
 */
public final class CommandValidation {

    private CommandValidation() {
    }

    /**
     * Reports a validation error to the sender if there is one.
     *
     * @param commonService service used to send the message
     * @param sender the sender to inform of a validation error
     * @param result the result of the validation
     * @return true if the validated value is valid, false if an error was reported
     */
    public static boolean isValid(CommonService commonService, CommandSender sender, ValidationResult result) {
        if (result.hasError()) {
            commonService.send(sender, result.getMessageKey(), result.getArgs());
            return false;
        }
        return true;
    }
}
