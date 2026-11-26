package fr.xephi.authme.command;

import fr.xephi.authme.command.executable.HelpCommand;
import fr.xephi.authme.permission.PermissionsManager;
import fr.xephi.authme.util.StringUtils;
import fr.xephi.authme.util.Utils;
import org.bukkit.command.CommandSender;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static fr.xephi.authme.command.FoundResultStatus.INCORRECT_ARGUMENTS;
import static fr.xephi.authme.command.FoundResultStatus.MISSING_BASE_COMMAND;
import static fr.xephi.authme.command.FoundResultStatus.UNKNOWN_LABEL;

/**
 * Maps incoming command parts to the correct {@link CommandDescription}.
 */
public class CommandMapper {

    /**
     * The class of the help command, to which the base label should also be passed in the arguments.
     */
    private static final Class<? extends ExecutableCommand> HELP_COMMAND_CLASS = HelpCommand.class;

    /** Prefix that may be used in the base label to explicitly refer to this plugin. */
    private static final String AUTHME_PREFIX = "authme:";

    private final Collection<CommandDescription> baseCommands;
    private final PermissionsManager permissionsManager;

    @Inject
    public CommandMapper(CommandInitializer commandInitializer, PermissionsManager permissionsManager) {
        this.baseCommands = commandInitializer.getCommands();
        this.permissionsManager = permissionsManager;
    }

    /**
     * Map incoming command parts to a command. This processes all parts and distinguishes the labels from arguments.
     *
     * @param sender The command sender (null if none applicable)
     * @param parts The parts to map to commands and arguments
     * @return The generated {@link FoundCommandResult}
     */
    public FoundCommandResult mapPartsToCommand(CommandSender sender, List<String> parts) {
        CommandDescription base = Utils.isCollectionEmpty(parts) ? null : getBaseCommand(parts.get(0));
        if (base == null) {
            return new FoundCommandResult(null, parts, null, 0.0, MISSING_BASE_COMMAND);
        }

        // Prefer labels: /register help goes to "Help command", not "Register command" with argument 'help'
        List<String> remainingParts = parts.subList(1, parts.size());
        CommandDescription childCommand = getSuitableChild(base, remainingParts);
        if (childCommand != null) {
            List<String> labels = parts.subList(0, 2);
            List<String> arguments = parts.subList(2, parts.size());
            if (HELP_COMMAND_CLASS == childCommand.getExecutableCommand()) {
                // For "/authme help register" the arguments are [register], but the help command needs
                // [authme, register] so that it can use the arguments as the path of the command to explain.
                arguments = new ArrayList<>(arguments);
                arguments.add(0, labels.get(0));
            }
            return createResult(sender, childCommand, labels, arguments);
        } else if (hasSuitableArgumentCount(base, remainingParts.size())) {
            return createResult(sender, base, parts.subList(0, 1), remainingParts);
        }

        return getCommandWithSmallestDifference(base, parts);
    }

    /**
     * Return all {@link ExecutableCommand} classes referenced in {@link CommandDescription} objects.
     *
     * @return all classes
     * @see CommandInitializer#getCommands
     */
    public Set<Class<? extends ExecutableCommand>> getCommandClasses() {
        Set<Class<? extends ExecutableCommand>> classes = new HashSet<>();
        for (CommandDescription command : baseCommands) {
            classes.add(command.getExecutableCommand());
            for (CommandDescription child : command.getChildren()) {
                classes.add(child.getExecutableCommand());
            }
        }
        return classes;
    }

    /**
     * Return the command whose label matches the given parts the best. This method is called when
     * a successful mapping could not be performed.
     *
     * @param base the base command
     * @param parts the command parts
     * @return the closest result
     */
    private static FoundCommandResult getCommandWithSmallestDifference(CommandDescription base, List<String> parts) {
        // Return the base command with incorrect arg count error if we only have one part
        if (parts.size() <= 1) {
            return new FoundCommandResult(base, parts, Collections.emptyList(), 0.0, INCORRECT_ARGUMENTS);
        }

        final String childLabel = parts.get(1);
        CommandDescription closestCommand = null;
        double minDifference = 0.0;
        for (CommandDescription child : base.getChildren()) {
            double difference = getLabelDifference(child, childLabel);
            if (closestCommand == null || Double.compare(difference, minDifference) < 0) {
                closestCommand = child;
                minDifference = difference;
            }
        }

        // base command may have no children, in which case we return the base command with incorrect arguments error
        if (closestCommand == null) {
            return new FoundCommandResult(
                base, parts.subList(0, 1), parts.subList(1, parts.size()), 0.0, INCORRECT_ARGUMENTS);
        }

        FoundResultStatus status = (minDifference == 0.0) ? INCORRECT_ARGUMENTS : UNKNOWN_LABEL;
        final int partsSize = parts.size();
        List<String> labels = parts.subList(0, Math.min(closestCommand.getLabelCount(), partsSize));
        List<String> arguments = (labels.size() == partsSize)
            ? Collections.emptyList()
            : parts.subList(labels.size(), partsSize);
        return new FoundCommandResult(closestCommand, labels, arguments, minDifference, status);
    }

    private CommandDescription getBaseCommand(String label) {
        String lowerLabel = label.toLowerCase(Locale.ROOT);
        String baseLabel = lowerLabel.startsWith(AUTHME_PREFIX)
            ? lowerLabel.substring(AUTHME_PREFIX.length()) : lowerLabel;
        for (CommandDescription command : baseCommands) {
            if (command.hasLabel(baseLabel)) {
                return command;
            }
        }
        return null;
    }

    /**
     * Return a child from a base command if the label and the argument count match.
     *
     * @param baseCommand The base command whose children should be checked
     * @param parts The command parts received from the invocation; the first item is the potential label and any
     *              other items are command arguments. The first initial part that led to the base command should not
     *              be present.
     *
     * @return A command if there was a complete match (including proper argument count), null otherwise
     */
    private static CommandDescription getSuitableChild(CommandDescription baseCommand, List<String> parts) {
        if (Utils.isCollectionEmpty(parts)) {
            return null;
        }

        final String label = parts.get(0).toLowerCase(Locale.ROOT);
        final int argumentCount = parts.size() - 1;
        for (CommandDescription child : baseCommand.getChildren()) {
            if (child.hasLabel(label) && hasSuitableArgumentCount(child, argumentCount)) {
                return child;
            }
        }
        return null;
    }

    private FoundCommandResult createResult(CommandSender sender, CommandDescription command,
                                             List<String> labels, List<String> arguments) {
        FoundResultStatus status = sender != null && !permissionsManager.hasPermission(sender, command.getPermission())
            ? FoundResultStatus.NO_PERMISSION : FoundResultStatus.SUCCESS;
        return new FoundCommandResult(command, labels, arguments, 0.0, status);
    }

    private static boolean hasSuitableArgumentCount(CommandDescription command, int argumentCount) {
        return argumentCount >= CommandUtils.getMinNumberOfArguments(command)
            && argumentCount <= CommandUtils.getMaxNumberOfArguments(command);
    }

    private static double getLabelDifference(CommandDescription command, String givenLabel) {
        if (command.getLabels().isEmpty()) {
            throw new IllegalStateException("Command does not have any labels set");
        }
        double minDifference = Double.MAX_VALUE;
        for (String label : command.getLabels()) {
            minDifference = Math.min(minDifference, StringUtils.getDifference(label, givenLabel));
        }
        return minDifference;
    }

}
