package fr.xephi.authme.command.executable;

import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import fr.xephi.authme.command.CommandDescription;
import fr.xephi.authme.command.CommandMapper;
import fr.xephi.authme.command.FoundCommandResult;
import fr.xephi.authme.command.FoundResultStatus;
import fr.xephi.authme.command.help.HelpProvider;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static fr.xephi.authme.command.FoundResultStatus.INCORRECT_ARGUMENTS;
import static fr.xephi.authme.command.FoundResultStatus.MISSING_BASE_COMMAND;
import static fr.xephi.authme.command.FoundResultStatus.SUCCESS;
import static fr.xephi.authme.command.FoundResultStatus.UNKNOWN_LABEL;
import static fr.xephi.authme.command.TestCommandsUtil.removeColors;
import static fr.xephi.authme.command.help.HelpProvider.SHOW_ALTERNATIVES;
import static fr.xephi.authme.command.help.HelpProvider.SHOW_CHILDREN;
import static fr.xephi.authme.command.help.HelpProvider.SHOW_COMMAND;
import static fr.xephi.authme.command.help.HelpProvider.SHOW_DESCRIPTION;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.hamcrest.MockitoHamcrest.argThat;

/**
 * Test for {@link HelpCommand}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.WARN)
public class HelpCommandTest {

    @InjectMocks
    private HelpCommand command;

    @Mock
    private CommandMapper commandMapper;

    @Mock
    private HelpProvider helpProvider;

    @Mock
    private CommandSender sender;

    @Captor
    private ArgumentCaptor<String> captor;

    @Test
    public void shouldHandleMissingBaseCommand() {
        // given
        List<String> arguments = asList("some", "command");
        FoundCommandResult foundCommandResult = new FoundCommandResult(null, null, null, 0.0, MISSING_BASE_COMMAND);
        given(commandMapper.mapPartsToCommand(sender, arguments)).willReturn(foundCommandResult);

        // when
        command.executeCommand(sender, arguments);

        // then
        verify(sender).sendMessage(argThat(containsString("Could not get base command")));
        verifyNoInteractions(helpProvider);
    }

    @Test
    public void shouldHandleWrongCommandWithSuggestion() {
        // given
        List<String> arguments = asList("authme", "ragister", "test");
        CommandDescription description = newCommandDescription("authme", "register");
        FoundCommandResult foundCommandResult = new FoundCommandResult(description, asList("authme", "ragister"),
            singletonList("test"), 0.1, UNKNOWN_LABEL);
        given(commandMapper.mapPartsToCommand(sender, arguments)).willReturn(foundCommandResult);

        // when
        command.executeCommand(sender, arguments);

        // then
        verify(sender).sendMessage(captor.capture());
        assertThat(removeColors(captor.getValue()), containsString("Assuming /authme register"));
        verify(helpProvider).outputHelp(sender, foundCommandResult, HelpProvider.ALL_OPTIONS);
    }

    @Test
    public void shouldHandleWrongCommandWithoutSuggestion() {
        List<String> arguments = asList("authme", "ragister", "test");
        FoundCommandResult foundCommandResult = new FoundCommandResult(null, asList("authme", "ragister"),
            singletonList("test"), 0.4, UNKNOWN_LABEL);
        given(commandMapper.mapPartsToCommand(sender, arguments)).willReturn(foundCommandResult);

        // when
        command.executeCommand(sender, arguments);

        // then
        verify(sender).sendMessage(captor.capture());
        assertThat(removeColors(captor.getValue()), containsString("Unknown command"));
        verifyNoInteractions(helpProvider);
    }

    @ParameterizedTest
    @MethodSource("helpCases")
    public void shouldOutputHelp(List<String> arguments, CommandDescription description,
                                 FoundResultStatus status, int expectedOptions) {
        FoundCommandResult foundCommandResult = new FoundCommandResult(description, arguments,
            Collections.emptyList(), 0.0, status);
        given(commandMapper.mapPartsToCommand(sender, arguments)).willReturn(foundCommandResult);

        // when
        command.executeCommand(sender, arguments);

        // then
        verify(sender, never()).sendMessage(anyString());
        verify(helpProvider).outputHelp(sender, foundCommandResult, expectedOptions);
    }

    private static Stream<Arguments> helpCases() {
        CommandDescription baseCommand = mock(CommandDescription.class);
        given(baseCommand.getLabelCount()).willReturn(1);
        CommandDescription childCommand = mock(CommandDescription.class);
        given(childCommand.getLabelCount()).willReturn(2);
        return Stream.of(
            Arguments.of(singletonList("authme"), baseCommand, SUCCESS,
                SHOW_DESCRIPTION | SHOW_COMMAND | SHOW_CHILDREN | SHOW_ALTERNATIVES),
            Arguments.of(asList("authme", "getpos"), childCommand, INCORRECT_ARGUMENTS, HelpProvider.ALL_OPTIONS));
    }

    private static CommandDescription newCommandDescription(String... labels) {
        CommandDescription parent = null;
        // iterate through the labels backwards so we can set the parent
        for (String label : labels) {
            CommandDescription description = mock(CommandDescription.class);
            given(description.getParent()).willReturn(parent);
            given(description.getLabels()).willReturn(singletonList(label));
            parent = description;
        }
        return parent;
    }

}
