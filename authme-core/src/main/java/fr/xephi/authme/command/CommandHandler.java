package fr.xephi.authme.command;

import ch.jalu.injector.factory.Factory;
import fr.xephi.authme.command.help.HelpProvider;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.message.Messages;
import fr.xephi.authme.permission.PermissionsManager;
import fr.xephi.authme.util.StringUtils;
import org.bukkit.command.CommandSender;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The AuthMe command handler, responsible for invoking the correct {@link ExecutableCommand} based on incoming
 * command labels or for displaying a help message for unknown command labels.
 */
public class CommandHandler {

    /**
     * The threshold for suggesting a similar command. If the difference is below this value, we will
     * ask the player whether he meant the similar command.
     */
    private static final double SUGGEST_COMMAND_THRESHOLD = 0.75;

    private final CommandMapper commandMapper;
    private final PermissionsManager permissionsManager;
    private final Messages messages;
    private final HelpProvider helpProvider;

    /**
     * Map with ExecutableCommand children. The key is the type of the value.
     */
    private final Map<Class<? extends ExecutableCommand>, ExecutableCommand> commands = new HashMap<>();

    @Inject
    CommandHandler(Factory<ExecutableCommand> commandFactory, CommandMapper commandMapper,
                   PermissionsManager permissionsManager, Messages messages, HelpProvider helpProvider) {
        this.commandMapper = commandMapper;
        this.permissionsManager = permissionsManager;
        this.messages = messages;
        this.helpProvider = helpProvider;
        commandMapper.getCommandClasses()
            .forEach(clazz -> commands.put(clazz, commandFactory.newInstance(clazz)));
    }

    /**
     * Map a command that was invoked to the proper {@link CommandDescription} or return a useful error
     * message upon failure.
     *
     * @param sender             The command sender.
     * @param bukkitCommandLabel The command label (Bukkit).
     * @param bukkitArgs         The command arguments (Bukkit).
     *
     * @return True if the command was executed, false otherwise.
     */
    public boolean processCommand(CommandSender sender, String bukkitCommandLabel, String[] bukkitArgs) {
        // Add the Bukkit command label to the front so we get a list like [authme, register, bobby, mysecret].
        // Blank arguments are filtered out centrally in the List overload below.
        List<String> parts = new ArrayList<>();
        parts.add(bukkitCommandLabel);
        if (bukkitArgs != null) {
            Collections.addAll(parts, bukkitArgs);
        }

        return processCommand(sender, parts);
    }

    /**
     * Processes a command represented as pre-split parts.
     *
     * @param sender the command sender
     * @param parts the command input split into label and arguments
     * @return true if the command was executed, false otherwise
     */
    public boolean processCommand(CommandSender sender, List<String> parts) {
        List<String> nonEmptyParts = parts == null ? Collections.emptyList()
            : parts.stream().filter(argument -> !StringUtils.isBlank(argument))
                .collect(Collectors.toList());
        FoundCommandResult result = commandMapper.mapPartsToCommand(sender, nonEmptyParts);
        handleCommandResult(sender, result);
        return !FoundResultStatus.MISSING_BASE_COMMAND.equals(result.getResultStatus());
    }

    /**
     * Processes the given {@link FoundCommandResult} for the provided command sender.
     *
     * @param sender the command sender who executed the command
     * @param result the command mapping result
     */
    private void handleCommandResult(CommandSender sender, FoundCommandResult result) {
        switch (result.getResultStatus()) {
            case SUCCESS:
                getExecutableCommand(result).executeCommand(sender, result.getArguments());
                break;
            case MISSING_BASE_COMMAND:
                messages.send(sender, MessageKey.COMMAND_FAILED_TO_PARSE);
                break;
            case INCORRECT_ARGUMENTS:
                sendImproperArgumentsMessage(sender, result);
                break;
            case UNKNOWN_LABEL:
                sendUnknownCommandMessage(sender, result);
                break;
            case NO_PERMISSION:
                messages.send(sender, MessageKey.NO_PERMISSION);
                break;
            default:
                throw new IllegalStateException("Unknown result status '" + result.getResultStatus() + "'");
        }
    }

    private ExecutableCommand getExecutableCommand(FoundCommandResult result) {
        return commands.get(result.getCommandDescription().getExecutableCommand());
    }

    /**
     * Show an "unknown command" message to the user and suggest an existing command if its similarity is within
     * the defined threshold.
     *
     * @param sender The command sender
     * @param result The command that was found during the mapping process
     */
    private void sendUnknownCommandMessage(CommandSender sender, FoundCommandResult result) {
        messages.send(sender, MessageKey.UNKNOWN_COMMAND);

        // Show a command suggestion if available and the difference isn't too big
        CommandDescription command = result.getCommandDescription();
        if (command != null && result.getDifference() <= SUGGEST_COMMAND_THRESHOLD) {
            messages.send(sender, MessageKey.COMMAND_DID_YOU_MEAN,
                CommandUtils.constructCommandPath(command));
        }

        messages.send(sender, MessageKey.COMMAND_SEE_HELP, result.getLabels().get(0) + " help");
    }

    private void sendImproperArgumentsMessage(CommandSender sender, FoundCommandResult result) {
        CommandDescription command = result.getCommandDescription();
        if (!permissionsManager.hasPermission(sender, command.getPermission())) {
            messages.send(sender, MessageKey.NO_PERMISSION);
            return;
        }

        MessageKey usageMessage = getExecutableCommand(result).getArgumentsMismatchMessage();
        if (usageMessage != null) {
            messages.send(sender, usageMessage);
            return;
        }

        messages.send(sender, MessageKey.COMMAND_INCORRECT_ARGUMENTS);
        helpProvider.outputHelp(sender, result, HelpProvider.SHOW_ARGUMENTS);

        List<String> labels = result.getLabels();
        String childLabel = labels.size() >= 2 ? labels.get(1) : "";
        String cmdPath = labels.get(0) + " help" + (childLabel.isEmpty() ? "" : " " + childLabel);
        messages.send(sender, MessageKey.COMMAND_DETAILED_HELP, cmdPath);
    }
}
