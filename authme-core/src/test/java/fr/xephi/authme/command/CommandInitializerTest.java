package fr.xephi.authme.command;

import fr.xephi.authme.util.StringUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Test for {@link CommandInitializer} to guarantee the integrity of the defined commands.
 */
class CommandInitializerTest {

    /**
     * Defines the maximum allowed depths for nesting CommandDescription instances.
     * Note that the depth starts at 0 (e.g. /authme), so a depth of 2 is something like /authme hello world
     */
    private static final int MAX_ALLOWED_DEPTH = 1;

    private static Collection<CommandDescription> commands;

    @BeforeAll
    static void initializeCommandCollection() {
        CommandInitializer commandInitializer = new CommandInitializer();
        commands = commandInitializer.getCommands();
    }

    @Test
    void shouldInitializeCommands() {
        // given/when/then
        // It obviously doesn't make sense to test much of the concrete data
        // that is being initialized; we just want to guarantee with this test
        // that data is indeed being initialized and we take a few "probes"
        assertThat(commands, hasSize(12));
        assertThat(commandsIncludeLabel(commands, "authme"), equalTo(true));
        assertThat(commandsIncludeLabel(commands, "register"), equalTo(true));
        assertThat(commandsIncludeLabel(commands, "help"), equalTo(false));
    }

    @Test
    void shouldNotBeNestedExcessively() {
        walkThroughCommands(commands,
            (command, depth) -> assertThat(depth <= MAX_ALLOWED_DEPTH, equalTo(true)), 0);
    }

    /** Ensure that all children of a command stored the parent. */
    @Test
    void shouldHaveConnectionBetweenParentAndChild() {
        // Checking that the parent has the current command as child is redundant: traversing the command tree
        // is only possible if the parent already knows the child.
        walkThroughCommands(commands, (command, depth) -> {
            for (CommandDescription child : command.getChildren()) {
                assertThat(command.equals(child.getParent()), equalTo(true));
            }
        }, 0);
    }

    @Test
    void shouldUseProperLowerCaseLabels() {
        Pattern invalidPattern = Pattern.compile("\\s");
        walkThroughCommands(commands, (command, depth) -> {
            for (String label : command.getLabels()) {
                if (!label.equals(label.toLowerCase(Locale.ROOT))) {
                    fail("Label '" + label + "' should be lowercase");
                } else if (invalidPattern.matcher(label).matches()) {
                    fail("Label '" + label + "' has whitespace");
                }
            }
        }, 0);
    }

    @Test
    void shouldNotDefineSameLabelTwice() {
        Set<String> commandMappings = new HashSet<>();
        walkThroughCommands(commands, (command, depth) -> {
            List<String> newMappings = getAbsoluteLabels(command);
            int initialSize = commandMappings.size();
            commandMappings.addAll(newMappings);
            // A Set only contains unique entries, so its size must grow by exactly the number of added bindings
            assertThat("All bindings are unique for command with bindings '" + newMappings + "'",
                commandMappings.size() == initialSize + newMappings.size(), equalTo(true));
        }, 0);
    }

    /**
     * The description should provide a very short description of the command and shouldn't end in a ".", whereas the
     * detailed description should be longer and end with a period.
     */
    @Test
    void shouldHaveProperDescription() {
        walkThroughCommands(commands, (command, depth) -> {
            String forCommandText = " for command with labels '" + command.getLabels() + "'";

            assertThat("has description" + forCommandText,
                StringUtils.isBlank(command.getDescription()), equalTo(false));
            assertThat("short description doesn't end in '.'" + forCommandText,
                command.getDescription().endsWith("."), equalTo(false));
            assertThat("has detailed description" + forCommandText,
                StringUtils.isBlank(command.getDetailedDescription()), equalTo(false));
            assertThat("detailed description ends in '.'" + forCommandText,
                command.getDetailedDescription().endsWith("."), equalTo(true));
        }, 0);
    }

    @Test
    void shouldHaveOptionalArgumentsAfterMandatoryOnes() {
        walkThroughCommands(commands, (command, depth) -> {
            boolean encounteredOptionalArg = false;
            for (CommandArgumentDescription argument : command.getArguments()) {
                if (argument.isOptional()) {
                    encounteredOptionalArg = true;
                } else if (encounteredOptionalArg) {
                    fail("Mandatory arguments should come before optional ones for command with labels '"
                        + command.getLabels() + "'");
                }
            }
        }, 0);
    }

    /**
     * Ensure that a command with children (i.e. a base command) doesn't define any arguments. This might otherwise
     * clash with the label of the child.
     */
    @Test
    void shouldNotHaveArgumentsIfCommandHasChildren() {
        walkThroughCommands(commands, (command, depth) -> {
            // Exception: a parent with a single "help" child may define arguments
            if (!command.getChildren().isEmpty() && !command.getArguments().isEmpty()
                    && (command.getChildren().size() != 1 || !command.getChildren().get(0).hasLabel("help"))) {
                fail("Parent command (labels='" + command.getLabels() + "') should not have any arguments");
            }
        }, 0);
    }

    /**
     * Tests that multiple CommandDescription instances pointing to the same ExecutableCommand use the same
     * count of arguments.
     */
    @Test
    void shouldPointToSameExecutableCommandWithConsistentArgumentCount() {
        Map<Class<? extends ExecutableCommand>, Integer> mandatoryArguments = new HashMap<>();
        Map<Class<? extends ExecutableCommand>, Integer> totalArguments = new HashMap<>();

        walkThroughCommands(commands, (command, depth) -> {
            checkArgumentCount(command, CommandUtils.getMinNumberOfArguments(command), mandatoryArguments);
            checkArgumentCount(command, CommandUtils.getMaxNumberOfArguments(command), totalArguments);
        }, 0);
    }


    // ------------
    // Helper methods
    // ------------
    private static void walkThroughCommands(Collection<CommandDescription> commands,
                                            BiConsumer<CommandDescription, Integer> consumer, int depth) {
        for (CommandDescription command : commands) {
            consumer.accept(command, depth);
            if (!command.getChildren().isEmpty()) {
                walkThroughCommands(command.getChildren(), consumer, depth + 1);
            }
        }
    }

    private static boolean commandsIncludeLabel(Iterable<CommandDescription> commands, String label) {
        for (CommandDescription command : commands) {
            if (command.getLabels().contains(label)) {
                return true;
            }
        }
        return false;
    }

    private static void checkArgumentCount(CommandDescription command, int argCount,
                                           Map<Class<? extends ExecutableCommand>, Integer> counts) {
        Class<? extends ExecutableCommand> clazz = command.getExecutableCommand();
        Integer existingCount = counts.get(clazz);
        if (existingCount == null) {
            counts.put(clazz, argCount);
        } else {
            String commandDescription = "Command with label '" + command.getLabels().get(0) + "' and parent '"
                + (command.getParent() == null ? "null" : command.getLabels().get(0)) + "' ";
            assertThat(commandDescription + "should point to " + clazz + " with arguments consistent to others",
                argCount, equalTo(existingCount));
        }
    }

    /**
     * Get the absolute binding that a command defines. Note: Assumes that only the passed command can have
     * multiple labels; only considering the first label for all of the command's parents.
     *
     * @param command The command to process
     *
     * @return List of all bindings that lead to the command
     */
    private static List<String> getAbsoluteLabels(CommandDescription command) {
        CommandDescription parent = command.getParent();
        String parentPath = (parent == null) ? "" : parent.getLabels().get(0) + " ";

        List<String> bindings = new ArrayList<>(command.getLabels().size());
        for (String label : command.getLabels()) {
            bindings.add(parentPath + label);
        }
        return bindings;
    }

}
