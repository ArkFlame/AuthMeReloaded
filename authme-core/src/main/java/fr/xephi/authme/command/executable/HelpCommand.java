package fr.xephi.authme.command.executable;

import fr.xephi.authme.command.CommandDescription;
import fr.xephi.authme.command.CommandMapper;
import fr.xephi.authme.command.CommandUtils;
import fr.xephi.authme.command.ExecutableCommand;
import fr.xephi.authme.command.FoundCommandResult;
import fr.xephi.authme.command.FoundResultStatus;
import fr.xephi.authme.command.help.HelpProvider;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import javax.inject.Inject;
import java.util.List;

import static fr.xephi.authme.command.FoundResultStatus.MISSING_BASE_COMMAND;
import static fr.xephi.authme.command.FoundResultStatus.UNKNOWN_LABEL;
import static fr.xephi.authme.command.help.HelpProvider.ALL_OPTIONS;
import static fr.xephi.authme.command.help.HelpProvider.SHOW_ALTERNATIVES;
import static fr.xephi.authme.command.help.HelpProvider.SHOW_CHILDREN;
import static fr.xephi.authme.command.help.HelpProvider.SHOW_COMMAND;
import static fr.xephi.authme.command.help.HelpProvider.SHOW_DESCRIPTION;

/**
 * Displays help information to a user.
 */
public class HelpCommand implements ExecutableCommand {

    @Inject
    private CommandMapper commandMapper;

    @Inject
    private HelpProvider helpProvider;


    // Convention: arguments is not the actual invoked arguments but the command that was invoked,
    // e.g. "/authme help register" would typically be arguments = [register], but here we pass [authme, register]
    @Override
    public void executeCommand(CommandSender sender, List<String> arguments) {
        FoundCommandResult result = commandMapper.mapPartsToCommand(sender, arguments);

        FoundResultStatus resultStatus = result.getResultStatus();
        if (MISSING_BASE_COMMAND.equals(resultStatus)) {
            sender.sendMessage(ChatColor.DARK_RED + "Could not get base command");
            return;
        }

        CommandDescription command = result.getCommandDescription();
        if (UNKNOWN_LABEL.equals(resultStatus)) {
            if (command == null) {
                sender.sendMessage(ChatColor.DARK_RED + "Unknown command");
                return;
            }
            sender.sendMessage(ChatColor.GOLD + "Assuming " + ChatColor.WHITE
                + CommandUtils.constructCommandPath(command));
        }

        if (command.getLabelCount() == 1) {
            helpProvider.outputHelp(sender, result,
                SHOW_COMMAND | SHOW_DESCRIPTION | SHOW_CHILDREN | SHOW_ALTERNATIVES);
        } else {
            helpProvider.outputHelp(sender, result, ALL_OPTIONS);
        }
    }

}
