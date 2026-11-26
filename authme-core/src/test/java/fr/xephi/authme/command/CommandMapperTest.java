package fr.xephi.authme.command;

import fr.xephi.authme.command.TestCommandsUtil.TestLoginCommand;
import fr.xephi.authme.command.TestCommandsUtil.TestRegisterCommand;
import fr.xephi.authme.command.TestCommandsUtil.TestUnregisterCommand;
import fr.xephi.authme.command.executable.HelpCommand;
import fr.xephi.authme.permission.PermissionNode;
import fr.xephi.authme.permission.PermissionsManager;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static fr.xephi.authme.command.TestCommandsUtil.getCommandWithLabel;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Test for {@link CommandMapper}.
 */
@ExtendWith(MockitoExtension.class)
class CommandMapperTest {

    private static List<CommandDescription> commands;

    private CommandMapper mapper;

    @Mock
    private PermissionsManager permissionsManager;

    @Mock
    private CommandInitializer commandInitializer;

    @Mock
    private CommandSender sender;

    @BeforeAll
    static void setUpCommandHandler() {
        commands = TestCommandsUtil.generateCommands();
    }

    @BeforeEach
    void setUpMocksAndMapper() {
        given(commandInitializer.getCommands()).willReturn(commands);
        mapper = new CommandMapper(commandInitializer, permissionsManager);
    }

    // -----------
    // mapPartsToCommand() tests
    // -----------
    @ParameterizedTest(name = "login command with permission {0}")
    @ValueSource(booleans = {true, false})
    void shouldMapLoginChildCommandWithPermission(boolean hasPermission) {
        // given
        List<String> parts = asList("authme", "login", "test1");
        setPermission(hasPermission);

        // when
        FoundCommandResult result = mapper.mapPartsToCommand(sender, parts);

        // then
        FoundResultStatus expectedStatus = hasPermission
            ? FoundResultStatus.SUCCESS
            : FoundResultStatus.NO_PERMISSION;
        assertThat(result.getCommandDescription(), equalTo(getCommandWithLabel(commands, "authme", "login")));
        assertThat(result.getResultStatus(), equalTo(expectedStatus));
        assertThat(result.getArguments(), contains("test1"));
        assertThat(result.getDifference(), equalTo(0.0));
        assertThat(result.getLabels(), equalTo(parts.subList(0, 2)));
    }

    @Test
    void shouldMapPartsToCommandWithNoCaseSensitivity() {
        // given
        List<String> parts = asList("Authme", "REG", "arg1", "arg2");
        setPermission(true);

        // when
        FoundCommandResult result = mapper.mapPartsToCommand(sender, parts);

        // then
        assertThat(result.getCommandDescription(), equalTo(getCommandWithLabel(commands, "authme", "register")));
        assertThat(result.getResultStatus(), equalTo(FoundResultStatus.SUCCESS));
        assertThat(result.getLabels(), equalTo(parts.subList(0, 2)));
        assertThat(result.getArguments(), contains("arg1", "arg2"));
        assertThat(result.getDifference(), equalTo(0.0));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("incorrectArgumentsCases")
    void shouldRejectCommandWithIncorrectArguments(List<String> parts, String parentLabel, String childLabel,
                                                   List<String> expectedLabels, List<String> expectedArguments) {
        // given
        CommandDescription expectedCommand = childLabel == null
            ? getCommandWithLabel(commands, parentLabel)
            : getCommandWithLabel(commands, parentLabel, childLabel);

        // when
        FoundCommandResult result = mapper.mapPartsToCommand(sender, parts);

        // then
        verifyNoInteractions(permissionsManager);
        assertThat(result.getCommandDescription(), equalTo(expectedCommand));
        assertThat(result.getResultStatus(), equalTo(FoundResultStatus.INCORRECT_ARGUMENTS));
        assertThat(result.getDifference(), equalTo(0.0));
        assertThat(result.getLabels(), equalTo(expectedLabels));
        assertThat(result.getArguments(), equalTo(expectedArguments));
    }

    static Stream<Arguments> incorrectArgumentsCases() {
        return Stream.of(
            Arguments.of(asList("authme", "register", "pass123", "pass123", "pass123"),
                "authme", "register", asList("authme", "register"), asList("pass123", "pass123", "pass123")),
            Arguments.of(asList("authme", "Reg"),
                "authme", "register", asList("authme", "Reg"), Collections.emptyList()),
            Arguments.of(singletonList("unregister"),
                "unregister", null, singletonList("unregister"), Collections.emptyList()),
            Arguments.of(asList("unregistER", "player1", "wrongArg"),
                "unregister", null, singletonList("unregistER"), asList("player1", "wrongArg")));
    }

    @Test
    void shouldSuggestCommandWithSimilarLabel() {
        // given
        List<String> parts = asList("authme", "reh", "pass123", "pass123");

        // when
        FoundCommandResult result = mapper.mapPartsToCommand(sender, parts);

        // then
        verifyNoInteractions(permissionsManager);
        assertThat(result.getCommandDescription(), equalTo(getCommandWithLabel(commands, "authme", "register")));
        assertThat(result.getResultStatus(), equalTo(FoundResultStatus.UNKNOWN_LABEL));
        assertThat(result.getDifference() < 0.75, equalTo(true));
        assertThat(result.getLabels(), equalTo(parts.subList(0, 2)));
        assertThat(result.getArguments(), contains("pass123", "pass123"));
    }

    /** In contrast to the previous test, we test a command request with a very apart label. */
    @Test
    void shouldSuggestMostSimilarCommand() {
        // given
        List<String> parts = asList("authme", "asdfawetawty4asdca");

        // when
        FoundCommandResult result = mapper.mapPartsToCommand(sender, parts);

        // then
        verifyNoInteractions(permissionsManager);
        assertThat(result.getCommandDescription(), not(nullValue()));
        assertThat(result.getResultStatus(), equalTo(FoundResultStatus.UNKNOWN_LABEL));
        assertThat(result.getDifference() > 0.75, equalTo(true));
        assertThat(result.getLabels(), equalTo(parts));
        assertThat(result.getArguments(), empty());
    }

    @Test
    void shouldHandleUnknownBase() {
        // given
        List<String> parts = asList("bogus", "label1", "arg1");

        // when
        FoundCommandResult result = mapper.mapPartsToCommand(sender, parts);

        // then
        verifyNoInteractions(permissionsManager);
        assertThat(result.getResultStatus(), equalTo(FoundResultStatus.MISSING_BASE_COMMAND));
        assertThat(result.getCommandDescription(), nullValue());
    }

    @Test
    void shouldHandleNullInput() {
        // given / when
        FoundCommandResult result = mapper.mapPartsToCommand(sender, null);

        // then
        assertThat(result.getResultStatus(), equalTo(FoundResultStatus.MISSING_BASE_COMMAND));
        assertThat(result.getCommandDescription(), nullValue());
    }

    @Test
    void shouldMapToBaseWithProperArguments() {
        // given
        List<String> parts = asList("Unreg", "player1");
        setPermission(true);

        // when
        FoundCommandResult result = mapper.mapPartsToCommand(sender, parts);

        // then
        assertThat(result.getResultStatus(), equalTo(FoundResultStatus.SUCCESS));
        assertThat(result.getCommandDescription(), equalTo(getCommandWithLabel(commands, "unregister")));
        assertThat(result.getDifference(), equalTo(0.0));
        assertThat(result.getArguments(), contains("player1"));
        assertThat(result.getLabels(), contains("Unreg"));
    }

    @Test
    void shouldPassCommandPathAsArgumentsToHelpCommand() {
        // given
        List<String> parts = asList("email", "helptest", "arg1");
        given(permissionsManager.hasPermission(eq(sender), isNull())).willReturn(true);

        // when
        FoundCommandResult result = mapper.mapPartsToCommand(sender, parts);

        // then
        assertThat(result.getResultStatus(), equalTo(FoundResultStatus.SUCCESS));
        assertThat(result.getCommandDescription(), equalTo(getCommandWithLabel(commands, "email", "helptest")));
        assertThat(result.getLabels(), contains("email", "helptest"));
        assertThat(result.getArguments(), contains("email", "arg1"));
        assertThat(result.getDifference(), equalTo(0.0));
    }

    @Test
    void shouldSupportAuthMePrefix() {
        // given
        List<String> parts = asList("authme:unregister", "Betty");
        setPermission(true);

        // when
        FoundCommandResult result = mapper.mapPartsToCommand(sender, parts);

        // then
        assertThat(result.getResultStatus(), equalTo(FoundResultStatus.SUCCESS));
        assertThat(result.getCommandDescription(), equalTo(getCommandWithLabel(commands, "unregister")));
    }

    @Test
    void shouldReturnExecutableCommandClasses() {
        // given / when
        Set<Class<? extends ExecutableCommand>> commandClasses = mapper.getCommandClasses();

        // then
        assertThat(commandClasses, containsInAnyOrder(ExecutableCommand.class, HelpCommand.class,
            TestLoginCommand.class, TestRegisterCommand.class, TestUnregisterCommand.class));
    }

    private void setPermission(boolean allowed) {
        given(permissionsManager.hasPermission(eq(sender), any(PermissionNode.class))).willReturn(allowed);
    }

}
