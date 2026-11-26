package fr.xephi.authme.command.help;

import fr.xephi.authme.command.CommandDescription;
import fr.xephi.authme.command.FoundCommandResult;
import fr.xephi.authme.command.FoundResultStatus;
import fr.xephi.authme.command.TestCommandsUtil;
import fr.xephi.authme.permission.AdminPermission;
import fr.xephi.authme.permission.DefaultPermission;
import fr.xephi.authme.permission.PermissionsManager;
import fr.xephi.authme.platform.BukkitCompatibilityAdapter;
import fr.xephi.authme.settings.Settings;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.internal.stubbing.answers.ReturnsArgumentAt;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static fr.xephi.authme.command.TestCommandsUtil.getCommandWithLabel;
import static fr.xephi.authme.command.TestCommandsUtil.removeColors;
import static fr.xephi.authme.command.help.HelpProvider.ALL_OPTIONS;
import static fr.xephi.authme.command.help.HelpProvider.SHOW_ALTERNATIVES;
import static fr.xephi.authme.command.help.HelpProvider.SHOW_ARGUMENTS;
import static fr.xephi.authme.command.help.HelpProvider.SHOW_CHILDREN;
import static fr.xephi.authme.command.help.HelpProvider.SHOW_COMMAND;
import static fr.xephi.authme.command.help.HelpProvider.SHOW_DESCRIPTION;
import static fr.xephi.authme.command.help.HelpProvider.SHOW_LONG_DESCRIPTION;
import static fr.xephi.authme.command.help.HelpProvider.SHOW_PERMISSIONS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Test for {@link HelpProvider}.
 */
@ExtendWith(MockitoExtension.class)
class HelpProviderTest {

    private static Collection<CommandDescription> commands;

    private HelpProvider helpProvider;
    @Mock
    private PermissionsManager permissionsManager;
    @Mock
    private HelpMessagesService helpMessagesService;
    @Mock
    private Settings settings;
    @Mock
    private BukkitCompatibilityAdapter compatibilityAdapter;
    @Mock
    private CommandSender sender;

    @BeforeAll
    static void setUpCommands() {
        commands = TestCommandsUtil.generateCommands();
    }

    @BeforeEach
    void initializeHelpProvider() {
        setDefaultHelpMessages(helpMessagesService);
        helpProvider = new HelpProvider(permissionsManager, helpMessagesService, settings, compatibilityAdapter);
    }

    @ParameterizedTest(name = "help output for {0}")
    @MethodSource("helpOutputCases")
    void shouldOutputExpectedHelpLines(List<String> commandLabels, List<String> resultLabels, int flags,
                                       List<String> expectedLines) {
        // given
        CommandDescription command = commandLabels.size() == 1
            ? getCommandWithLabel(commands, commandLabels.get(0))
            : getCommandWithLabel(commands, commandLabels.get(0), commandLabels.get(1));
        FoundCommandResult result = newFoundResult(command, resultLabels);

        // when
        helpProvider.outputHelp(sender, result, flags);

        // then
        assertThat(getLines(sender), equalTo(expectedLines));
    }

    static Stream<Arguments> helpOutputCases() {
        return Stream.of(
            Arguments.of(Arrays.asList("authme", "login"), Arrays.asList("authme", "login"),
                SHOW_COMMAND | SHOW_LONG_DESCRIPTION | SHOW_DESCRIPTION,
                Arrays.asList("Header", "Command: /authme login <password>", "Short description: login cmd",
                    "Detailed description:", " 'login' test command")),
            Arguments.of(Arrays.asList("authme", "register"), Arrays.asList("authme", "reg"), SHOW_ARGUMENTS,
                Arrays.asList("Header", "Arguments:", " password: 'password' argument description",
                    " confirmation: 'confirmation' argument description")),
            Arguments.of(Collections.singletonList("email"), Collections.singletonList("email"), SHOW_ARGUMENTS,
                Arrays.asList("Header", "Arguments:", " player: 'player' argument description (Optional)")),
            Arguments.of(Arrays.asList("authme", "register"), Arrays.asList("authme", "reg"), SHOW_ALTERNATIVES,
                Arrays.asList("Header", "Alternatives:", " /authme register <password> <confirmation>",
                    " /authme r <password> <confirmation>")),
            Arguments.of(Arrays.asList("authme", "register"), Arrays.asList("authme", "ragister"), SHOW_COMMAND,
                Arrays.asList("Header", "Command: /authme register <password> <confirmation>")),
            Arguments.of(Collections.singletonList("unregister"), Collections.singletonList("unreg"),
                SHOW_COMMAND | SHOW_ALTERNATIVES,
                Arrays.asList("Header", "Command: /unreg <player>", "Alternatives:", " /unregister <player>")));
    }

    /** Verifies that a section with no content only produces the help banner. */
    @ParameterizedTest(name = "no output for empty {0}")
    @MethodSource("emptySectionCases")
    void shouldNotShowAnythingForEmptySection(String section, String parentLabel, String childLabel,
                                              List<String> labels, int flags) {
        // given
        CommandDescription command = childLabel == null
            ? getCommandWithLabel(commands, parentLabel)
            : getCommandWithLabel(commands, parentLabel, childLabel);
        FoundCommandResult result = newFoundResult(command, labels);

        // when
        helpProvider.outputHelp(sender, result, flags);

        // then
        assertThat(getLines(sender), hasSize(1));
    }

    static Stream<Arguments> emptySectionCases() {
        return Stream.of(
            Arguments.of("arguments", "authme", null, Collections.singletonList("authme"), SHOW_ARGUMENTS),
            Arguments.of("permissions", "authme", null, Collections.singletonList("authme"), SHOW_PERMISSIONS),
            Arguments.of("alternatives", "authme", "login", Arrays.asList("authme", "login"), SHOW_ALTERNATIVES),
            Arguments.of("children", "authme", "register", Collections.singletonList("authme"), SHOW_CHILDREN));
    }

    @ParameterizedTest(name = "{0} ({4})")
    @CsvSource({"unreg, true, true, true, Has permission",
        "unregister, false, false, false, No permission"})
    void shouldShowAndEvaluatePermissions(String label, boolean isOp, boolean hasNodePermission,
                                          boolean hasDefaultPermission, String expectedResult) {
        // given
        CommandDescription command = getCommandWithLabel(commands, "unregister");
        FoundCommandResult result = newFoundResult(command, Collections.singletonList(label));
        given(sender.isOp()).willReturn(isOp);
        given(permissionsManager.hasPermission(sender, AdminPermission.UNREGISTER)).willReturn(hasNodePermission);
        given(permissionsManager.hasPermission(sender, command.getPermission())).willReturn(hasDefaultPermission);

        // when
        helpProvider.outputHelp(sender, result, SHOW_PERMISSIONS);

        // then
        List<String> lines = getLines(sender);
        assertThat(lines, hasSize(5));
        assertThat(lines.get(1), containsString("Permissions:"));
        assertThat(lines.get(2), containsString(AdminPermission.UNREGISTER.getNode() + " (" + expectedResult + ")"));
        assertThat(lines.get(3), containsString("Default: Op only (" + expectedResult + ")"));
        assertThat(lines.get(4), containsString("Result: " + expectedResult));
    }

    @Test
    void shouldNotShowAnythingForNullPermissionsOnCommand() {
        // given
        CommandDescription command = mock(CommandDescription.class);
        given(command.getPermission()).willReturn(null);
        given(command.getLabels()).willReturn(Collections.singletonList("test"));
        FoundCommandResult result = newFoundResult(command, Collections.singletonList("test"));

        // when
        helpProvider.outputHelp(sender, result, SHOW_PERMISSIONS);

        // then
        List<String> lines = getLines(sender);
        assertThat(lines, hasSize(1));
    }

    @Test
    void shouldShowChildren() {
        // given
        CommandDescription command = getCommandWithLabel(commands, "authme");
        FoundCommandResult result = newFoundResult(command, Collections.singletonList("authme"));
        given(helpMessagesService.getDescription(getCommandWithLabel(commands, "authme", "login"), null))
            .willReturn("Command for login [localized]");
        given(helpMessagesService.getDescription(getCommandWithLabel(commands, "authme", "register"), null))
            .willReturn("Registration command [localized]");

        // when
        helpProvider.outputHelp(sender, result, SHOW_CHILDREN);

        // then
        List<String> lines = getLines(sender);
        assertThat(lines, hasSize(4));
        assertThat(lines.get(1), equalTo("Children:"));
        assertThat(lines.get(2), equalTo(" /authme login: Command for login [localized]"));
        assertThat(lines.get(3), equalTo(" /authme register: Registration command [localized]"));
    }

    @Test
    void shouldHandleUnboundFoundCommandResult() {
        // given
        FoundCommandResult result = new FoundCommandResult(null, Arrays.asList("authme", "test"),
            Collections.emptyList(), 0.0, FoundResultStatus.UNKNOWN_LABEL);

        // when
        helpProvider.outputHelp(sender, result, ALL_OPTIONS);

        // then
        List<String> lines = getLines(sender);
        assertThat(lines, hasSize(1));
        assertThat(lines.get(0), containsString("Failed to retrieve any help information"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("filterCorrectLabelsCases")
    void shouldFilterCorrectLabels(List<String> labels, List<String> expectedLabels) {
        // given
        CommandDescription command = getCommandWithLabel(commands, "authme", "register");

        // when
        List<String> result = HelpProvider.filterCorrectLabels(command, labels);

        // then
        assertThat(result, equalTo(expectedLabels));
    }

    static Stream<Arguments> filterCorrectLabelsCases() {
        return Stream.of(
            Arguments.of(Arrays.asList("authme", "reg"), Arrays.asList("authme", "reg")),
            Arguments.of(Arrays.asList("authme", "wrong"), Arrays.asList("authme", "register")));
    }

    @Test
    void shouldDisableSectionsWithEmptyTranslations() {
        // given
        given(helpMessagesService.getMessage(HelpSection.DETAILED_DESCRIPTION)).willReturn("");
        given(helpMessagesService.getMessage(HelpSection.ALTERNATIVES)).willReturn("");
        given(helpMessagesService.getMessage(HelpSection.PERMISSIONS)).willReturn("");

        CommandDescription command = getCommandWithLabel(commands, "authme", "register");
        FoundCommandResult result = newFoundResult(command, Collections.singletonList("authme"));

        // when
        helpProvider.outputHelp(sender, result, ALL_OPTIONS);

        // then
        List<String> lines = getLines(sender);
        assertThat(lines, hasSize(6));
        assertThat(lines.get(0), equalTo("Header"));
        assertThat(lines.get(1), equalTo("Command: /authme register <password> <confirmation>"));
        assertThat(lines.get(2), equalTo("Short description: register cmd"));
        assertThat(lines.get(3), equalTo("Arguments:"));
        assertThat(lines.get(4), containsString("'password' argument description"));
        assertThat(lines.get(5), containsString("'confirmation' argument description"));
    }

    @Test
    void shouldNotReturnAnythingForAllDisabledSections() {
        // given
        given(helpMessagesService.getMessage(HelpSection.COMMAND)).willReturn("");
        given(helpMessagesService.getMessage(HelpSection.ALTERNATIVES)).willReturn("");
        given(helpMessagesService.getMessage(HelpSection.PERMISSIONS)).willReturn("");

        CommandDescription command = getCommandWithLabel(commands, "authme", "register");
        FoundCommandResult result = newFoundResult(command, Collections.singletonList("authme"));

        // when
        helpProvider.outputHelp(sender, result, SHOW_COMMAND | SHOW_ALTERNATIVES | SHOW_PERMISSIONS);

        // then
        verify(sender, never()).sendMessage(anyString());
    }

    @Test
    void shouldSkipEmptyHeader() {
        // given
        given(helpMessagesService.getMessage(eq(HelpMessage.HEADER), isNull())).willReturn("");
        CommandDescription command = getCommandWithLabel(commands, "authme", "register");
        FoundCommandResult result = newFoundResult(command, Collections.singletonList("authme"));

        // when
        helpProvider.outputHelp(sender, result, SHOW_COMMAND);

        // then
        List<String> lines = getLines(sender);
        assertThat(lines, hasSize(1));
        assertThat(lines.get(0), equalTo("Command: /authme register <password> <confirmation>"));
    }

    /**
     * Generate an instance of {@link FoundCommandResult} with the given command and labels. All other fields aren't
     * retrieved by {@link HelpProvider} and so are initialized to default values for the tests.
     *
     * @param command The command description
     * @param labels The labels of the command (as in a real use case, they do not have to be correct)
     * @return The generated FoundCommandResult object
     */
    private static FoundCommandResult newFoundResult(CommandDescription command, List<String> labels) {
        return new FoundCommandResult(command, labels, Collections.emptyList(), 0.0, FoundResultStatus.SUCCESS);
    }

    private static List<String> getLines(CommandSender sender) {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(sender, atLeastOnce()).sendMessage(captor.capture());
        return captor.getAllValues().stream().map(s -> removeColors(s)).collect(Collectors.toList());
    }

    /** Builds the human-readable test text for an enum constant, e.g. SHOW_COMMAND becomes "Show command". */
    private static String displayText(Enum<?> value) {
        String text = value.name().replace("_", " ").toLowerCase(Locale.ROOT);
        return text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1);
    }

    private static void setDefaultHelpMessages(HelpMessagesService helpMessagesService) {
        lenient().when(helpMessagesService.buildLocalizedDescription(any(CommandDescription.class)))
            .thenAnswer(new ReturnsArgumentAt(0));
        lenient().when(helpMessagesService.buildLocalizedDescription(any(CommandDescription.class), isNull()))
            .thenAnswer(new ReturnsArgumentAt(0));
        for (HelpMessage key : HelpMessage.values()) {
            String returnValue = displayText(key);
            lenient().when(helpMessagesService.getMessage(key)).thenReturn(returnValue);
            lenient().when(helpMessagesService.getMessage(eq(key), isNull())).thenReturn(returnValue);
        }
        for (DefaultPermission permission : DefaultPermission.values()) {
            String returnValue = displayText(permission);
            lenient().when(helpMessagesService.getMessage(permission)).thenReturn(returnValue);
            lenient().when(helpMessagesService.getMessage(eq(permission), isNull())).thenReturn(returnValue);
        }
        for (HelpSection section : HelpSection.values()) {
            String returnValue = displayText(section);
            lenient().when(helpMessagesService.getMessage(section)).thenReturn(returnValue);
            lenient().when(helpMessagesService.getMessage(eq(section), isNull())).thenReturn(returnValue);
        }
    }

}
