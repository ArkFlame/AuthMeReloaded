package fr.xephi.authme.command.executable;

import fr.xephi.authme.command.CommandMapper;
import fr.xephi.authme.command.ExecutableCommand;
import fr.xephi.authme.command.FoundCommandResult;
import fr.xephi.authme.command.help.HelpProvider;
import org.bukkit.command.CommandSender;

import javax.inject.Inject;
import java.util.Collections;
import java.util.List;

/**
 * Base command that outputs the help for the children of a given base command.
 */
public abstract class BaseHelpCommand implements ExecutableCommand {

    @Inject
    private CommandMapper commandMapper;

    @Inject
    private HelpProvider helpProvider;

    @Override
    public final void executeCommand(CommandSender sender, List<String> arguments) {
        FoundCommandResult result = commandMapper.mapPartsToCommand(sender, Collections.singletonList(getBaseLabel()));
        helpProvider.outputHelp(sender, result, HelpProvider.SHOW_CHILDREN);
    }

    /**
     * @return the label of the base command whose children should be shown
     */
    protected abstract String getBaseLabel();
}
