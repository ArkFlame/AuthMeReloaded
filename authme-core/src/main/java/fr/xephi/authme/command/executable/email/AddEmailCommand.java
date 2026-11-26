package fr.xephi.authme.command.executable.email;

import fr.xephi.authme.message.MessageKey;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Command for setting an email to an account.
 */
public class AddEmailCommand extends AbstractEmailCommand {

    @Override
    public void runCommand(Player player, List<String> arguments) {
        String email = arguments.get(0);
        String emailConfirmation = arguments.get(1);

        if (!isEmailValid(player, email, MessageKey.INVALID_EMAIL)) {
            return;
        }
        if (email.equals(emailConfirmation)) {
            getManagement().performAddEmail(player, email);
        } else {
            getCommonService().send(player, MessageKey.CONFIRM_EMAIL_MESSAGE);
        }
    }

    @Override
    public MessageKey getArgumentsMismatchMessage() {
        return MessageKey.USAGE_ADD_EMAIL;
    }
}
