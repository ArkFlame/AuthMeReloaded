package fr.xephi.authme.command.executable.authme;

import fr.xephi.authme.command.executable.CommandValidation;
import fr.xephi.authme.process.Management;
import fr.xephi.authme.service.ValidationService;
import org.bukkit.command.CommandSender;

import javax.inject.Inject;
import java.util.List;

/**
 * Admin command for changing a player's password.
 */
public class ChangePasswordAdminCommand extends AbstractPlayerAuthCommand {

    @Inject
    private ValidationService validationService;

    @Inject
    private Management management;

    @Override
    public void executeCommand(CommandSender sender, List<String> arguments) {
        final String playerName = arguments.get(0);
        final String playerPass = arguments.get(1);

        if (CommandValidation.isValid(getCommonService(), sender,
            validationService.validatePassword(playerPass, playerName))) {
            management.performPasswordChangeAsAdmin(sender, playerName, playerPass);
        }
    }
}
