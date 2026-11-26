package fr.xephi.authme.command;

import ch.jalu.injector.factory.Factory;
import com.google.common.collect.Sets;
import fr.xephi.authme.command.TestCommandsUtil.TestLoginCommand;
import fr.xephi.authme.command.TestCommandsUtil.TestRegisterCommand;
import fr.xephi.authme.command.TestCommandsUtil.TestUnregisterCommand;
import fr.xephi.authme.command.help.HelpProvider;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.message.Messages;
import fr.xephi.authme.permission.PermissionsManager;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static fr.xephi.authme.command.FoundResultStatus.INCORRECT_ARGUMENTS;
import static fr.xephi.authme.command.FoundResultStatus.SUCCESS;
import static fr.xephi.authme.command.FoundResultStatus.UNKNOWN_LABEL;
import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Test for {@link CommandHandler}.
 */
// Justification: It's more readable to use asList() everywhere in the test when we often generated two lists where one
// often consists of only one element, e.g. myMethod(asList("authme"), asList("my", "args"), ...)
@SuppressWarnings("ArraysAsListWithZeroOrOneArgument")
@ExtendWith(MockitoExtension.class)
class CommandHandlerTest {

    private CommandHandler handler;
    private CommandSender sender;
    private CommandDescription command;

    @Mock
    private Factory<ExecutableCommand> commandFactory;
    @Mock
    private CommandMapper commandMapper;
    @Mock
    private PermissionsManager permissionsManager;
    @Mock
    private Messages messages;
    @Mock
    private HelpProvider helpProvider;

    private final Map<Class<? extends ExecutableCommand>, ExecutableCommand> mockedCommands = new HashMap<>();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void initializeCommandMapper() {
        given(commandMapper.getCommandClasses()).willReturn(Sets.newHashSet(
            ExecutableCommand.class, TestLoginCommand.class, TestRegisterCommand.class, TestUnregisterCommand.class));
        setInjectorToMockExecutableCommandClasses();

        sender = mock(CommandSender.class);
        command = mock(CommandDescription.class);
        handler = new CommandHandler(commandFactory, commandMapper, permissionsManager, messages, helpProvider);
    }

    /** Stubs the mapper to return a result for the given command path and arguments. */
    private void givenMapping(List<String> labels, List<String> arguments, double difference,
                              FoundResultStatus status) {
        given(commandMapper.mapPartsToCommand(any(CommandSender.class), anyList()))
            .willReturn(new FoundCommandResult(command, labels, arguments, difference, status));
    }

    private void verifyMappedParts(String... parts) {
        verify(commandMapper).mapPartsToCommand(sender, asList(parts));
    }

    /**
     * Makes the injector return a mock when {@link ch.jalu.injector.Injector#newInstance(Class)} is invoked
     * with (a child of) ExecutableCommand.class. The mocks the injector creates are stored in {@link #mockedCommands}.
     * <p>
     * The {@link CommandMapper} is mocked in {@link #initializeCommandMapper()} to return certain test classes.
     */
    @SuppressWarnings("unchecked")
    private void setInjectorToMockExecutableCommandClasses() {
        given(commandFactory.newInstance(any(Class.class))).willAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                Class<?> clazz = invocation.getArgument(0);
                if (ExecutableCommand.class.isAssignableFrom(clazz)) {
                    Class<? extends ExecutableCommand> commandClass = (Class<? extends ExecutableCommand>) clazz;
                    ExecutableCommand mock = mock(commandClass);
                    mockedCommands.put(commandClass, mock);
                    return mock;
                }
                throw new IllegalStateException("Unexpected class '" + clazz.getName()
                    + "': Not a child of ExecutableCommand");
            }
        });
    }

    @ParameterizedTest(name = "pre-split parts: {0}")
    @ValueSource(booleans = {false, true})
    void shouldCallMappedCommandWithArgs(boolean preSplitParts) {
        // given
        givenMapping(asList("Authme", "Login"), asList("myPass"), 0.0, SUCCESS);
        doReturn(TestLoginCommand.class).when(command).getExecutableCommand();

        // when
        if (preSplitParts) {
            handler.processCommand(sender, asList("Authme", "Login", "myPass"));
        } else {
            handler.processCommand(sender, "Authme", new String[]{"Login", "myPass"});
        }

        // then
        ExecutableCommand executableCommand = mockedCommands.get(TestLoginCommand.class);
        verifyMappedParts("Authme", "Login", "myPass");
        verify(executableCommand).executeCommand(sender, asList("myPass"));
        // Ensure that no error message was issued to the command sender
        verify(sender, never()).sendMessage(anyString());
    }

    @ParameterizedTest(name = "status: {0}")
    @CsvSource({"NO_PERMISSION, NO_PERMISSION", "MISSING_BASE_COMMAND, COMMAND_FAILED_TO_PARSE"})
    void shouldNotCallExecutableOnUnhandledResult(FoundResultStatus status, MessageKey messageKey) {
        // given
        givenMapping(asList("unreg"), asList("testPlayer"), 0.0, status);

        // when
        handler.processCommand(sender, "unreg", new String[]{"testPlayer"});

        // then
        verifyMappedParts("unreg", "testPlayer");
        verify(command, never()).getExecutableCommand();
        verify(messages).send(sender, messageKey);
        verify(sender, never()).sendMessage(anyString());
    }

    @ParameterizedTest(name = "custom mismatch message: {0}")
    @ValueSource(booleans = {false, true})
    void shouldNotCallExecutableForWrongArguments(boolean useCustomMessage) {
        // given
        given(command.getExecutableCommand()).willReturn((Class) TestUnregisterCommand.class);
        if (useCustomMessage) {
            given(mockedCommands.get(TestUnregisterCommand.class).getArgumentsMismatchMessage())
                .willReturn(MessageKey.USAGE_RECOVER_EMAIL);
        }
        givenMapping(asList("unreg"), asList("testPlayer"), 0.0, INCORRECT_ARGUMENTS);
        given(permissionsManager.hasPermission(sender, command.getPermission())).willReturn(true);

        // when
        handler.processCommand(sender, "unreg", new String[]{"testPlayer"});

        // then
        verifyMappedParts("unreg", "testPlayer");
        if (useCustomMessage) {
            verify(messages).send(sender, MessageKey.USAGE_RECOVER_EMAIL);
        } else {
            verify(messages).send(sender, MessageKey.COMMAND_INCORRECT_ARGUMENTS);
            verify(helpProvider).outputHelp(eq(sender), any(FoundCommandResult.class), eq(HelpProvider.SHOW_ARGUMENTS));
            verify(messages).send(eq(sender), eq(MessageKey.COMMAND_DETAILED_HELP), anyString());
        }
        verify(sender, never()).sendMessage(anyString());
    }

    @Test
    void shouldNotCallExecutableForWrongArgumentsAndPermissionDenied() {
        // given
        givenMapping(asList("unreg"), asList("testPlayer"), 0.0, INCORRECT_ARGUMENTS);
        given(permissionsManager.hasPermission(sender, command.getPermission())).willReturn(false);

        // when
        handler.processCommand(sender, "unreg", new String[]{"testPlayer"});

        // then
        verifyMappedParts("unreg", "testPlayer");
        verify(command, never()).getExecutableCommand();
        verify(messages).send(sender, MessageKey.NO_PERMISSION);
    }

    @ParameterizedTest(name = "suggestion: {1}")
    @CsvSource({"0.01, true", "1.0, false"})
    void shouldNotCallExecutableForUnknownLabel(double difference, boolean hasSuggestion) {
        // given
        if (hasSuggestion) {
            given(command.getLabels()).willReturn(Collections.singletonList("test_cmd"));
        }
        givenMapping(asList("unreg"), asList("testPlayer"), difference, UNKNOWN_LABEL);

        // when
        handler.processCommand(sender, "unreg", new String[]{"testPlayer"});

        // then
        verifyMappedParts("unreg", "testPlayer");
        verify(command, never()).getExecutableCommand();
        verify(messages).send(sender, MessageKey.UNKNOWN_COMMAND);
        if (hasSuggestion) {
            ArgumentCaptor<String> cmdCaptor = ArgumentCaptor.forClass(String.class);
            verify(messages).send(eq(sender), eq(MessageKey.COMMAND_DID_YOU_MEAN), cmdCaptor.capture());
            assertThat(cmdCaptor.getValue(), containsString("test_cmd"));
        }
        verify(messages).send(eq(sender), eq(MessageKey.COMMAND_SEE_HELP), anyString());
        verify(sender, never()).sendMessage(anyString());
    }

    @Test
    void shouldStripWhitespace() {
        // given
        doReturn(TestRegisterCommand.class).when(command).getExecutableCommand();
        givenMapping(asList("AuthMe", "REGISTER"), asList("testArg"), 0.0, SUCCESS);

        // when
        handler.processCommand(sender, "AuthMe", new String[]{" ", "", "REGISTER", "  ", "testArg", " "});

        // then
        ExecutableCommand executableCommand = mockedCommands.get(TestRegisterCommand.class);
        verifyMappedParts("AuthMe", "REGISTER", "testArg");
        verify(executableCommand).executeCommand(sender, asList("testArg"));
        verify(sender, never()).sendMessage(anyString());
    }

}
