package fr.xephi.authme.command;

import org.bukkit.ChatColor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * Test for {@link CommandUtils}.
 */
class CommandUtilsTest {

    private static Collection<CommandDescription> commands;

    @BeforeAll
    static void setUpTestCommands() {
        commands = Collections.unmodifiableCollection(TestCommandsUtil.generateCommands());
    }

    @Test
    void shouldReturnCommandPath() {
        // given
        CommandDescription base = CommandDescription.builder()
            .labels("authme", "auth")
            .description("Base")
            .detailedDescription("Test base command.")
            .executableCommand(ExecutableCommand.class)
            .register();
        CommandDescription command = CommandDescription.builder()
            .parent(base)
            .labels("help", "h", "?")
            .description("Child")
            .detailedDescription("Test child command.")
            .executableCommand(ExecutableCommand.class)
            .register();

        // when
        String commandPath = CommandUtils.constructCommandPath(command);

        // then
        assertThat(commandPath, equalTo("/authme help"));
    }

    @Test
    void shouldComputeMinAndMaxOnEmptyCommand() {
        // given
        CommandDescription command = getBuilderForArgsTest().register();

        // when / then
        checkArgumentCount(command, 0, 0);
    }

    @Test
    void shouldComputeMinAndMaxOnCommandWithMandatoryArgs() {
        // given
        CommandDescription command = getBuilderForArgsTest()
            .withArgument("Test", "Arg description", false)
            .withArgument("Test22", "Arg description 2", false)
            .register();

        // when / then
        checkArgumentCount(command, 2, 2);
    }

    @Test
    void shouldComputeMinAndMaxOnCommandIncludingOptionalArgs() {
        // given
        CommandDescription command = getBuilderForArgsTest()
            .withArgument("arg1", "Arg description", false)
            .withArgument("arg2", "Arg description 2", true)
            .withArgument("arg3", "Arg description 3", true)
            .register();

        // when / then
        checkArgumentCount(command, 1, 3);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("buildSyntaxCases")
    void shouldFormatCommandSyntax(String name, String parentLabel, String childLabel,
                                   List<String> labels, String expectedSyntax) {
        // given
        CommandDescription command = childLabel == null
            ? TestCommandsUtil.getCommandWithLabel(commands, parentLabel)
            : TestCommandsUtil.getCommandWithLabel(commands, parentLabel, childLabel);

        // when
        String result = CommandUtils.buildSyntax(command, labels);

        // then
        assertThat(result, equalTo(expectedSyntax));
    }

    static Stream<Arguments> buildSyntaxCases() {
        return Stream.of(
            Arguments.of("simple argument", "authme", null,
                Collections.singletonList("authme"), ChatColor.WHITE + "/authme" + ChatColor.YELLOW),
            Arguments.of("multiple arguments", "authme", "register", Arrays.asList("authme", "reg"),
                ChatColor.WHITE + "/authme" + ChatColor.YELLOW + " reg <password> <confirmation>"),
            Arguments.of("optional argument", "email", null,
                Collections.singletonList("email"), ChatColor.WHITE + "/email" + ChatColor.YELLOW + " [player]"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("splitInputCases")
    void shouldSplitInput(String input, String[] expectedParts) {
        assertThat(CommandUtils.splitInput(input), equalTo(Arrays.asList(expectedParts)));
    }

    static Stream<Arguments> splitInputCases() {
        return Stream.of(
            Arguments.of(null, new String[0]),
            Arguments.of("   \t  \n ", new String[0]),
            Arguments.of("/", new String[0]),
            Arguments.of("/   \t   ", new String[0]),
            Arguments.of("authme", new String[]{"authme"}),
            Arguments.of("/authme", new String[]{"authme"}),
            Arguments.of("   /authme register   ", new String[]{"authme", "register"}),
            Arguments.of("/authme    register     player    password",
                new String[]{"authme", "register", "player", "password"}),
            Arguments.of("/authme\tregister\nplayer\r\npassword",
                new String[]{"authme", "register", "player", "password"}),
            Arguments.of("/   authme   register", new String[]{"authme", "register"}),
            Arguments.of("//authme register", new String[]{"/authme", "register"}),
            Arguments.of("/login p@ss-word_123", new String[]{"login", "p@ss-word_123"}));
    }

    private static void checkArgumentCount(CommandDescription command, int expectedMin, int expectedMax) {
        assertThat(CommandUtils.getMinNumberOfArguments(command), equalTo(expectedMin));
        assertThat(CommandUtils.getMaxNumberOfArguments(command), equalTo(expectedMax));
    }

    private static CommandDescription.CommandBuilder getBuilderForArgsTest() {
        return CommandDescription.builder()
            .labels("authme", "auth")
            .description("Base")
            .detailedDescription("Test base command.")
            .executableCommand(ExecutableCommand.class);
    }
}
