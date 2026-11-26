package fr.xephi.authme.command;

import ch.jalu.injector.factory.Factory;
import fr.xephi.authme.command.help.HelpProvider;
import fr.xephi.authme.message.Messages;
import fr.xephi.authme.permission.PermissionsManager;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static java.util.Arrays.asList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * Tests edge cases of the command parsing entry points in {@link CommandHandler}:
 * empty or blank arguments, missing arguments and non-ASCII (unicode) arguments.
 */
@ExtendWith(MockitoExtension.class)
class CommandParsingEdgeCaseTest {

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
    @Mock
    private CommandSender sender;

    private CommandHandler handler;

    @BeforeEach
    void setUp() {
        given(commandMapper.getCommandClasses()).willReturn(Collections.emptySet());
        given(commandMapper.mapPartsToCommand(any(CommandSender.class), anyList()))
            .willReturn(new FoundCommandResult(null, Collections.emptyList(), Collections.emptyList(),
                0.0, FoundResultStatus.MISSING_BASE_COMMAND));
        handler = new CommandHandler(commandFactory, commandMapper, permissionsManager, messages, helpProvider);
    }

    @Test
    void shouldStripBlankArgumentsFromPreSplitParts() {
        // when
        handler.processCommand(sender, asList("AuthMe", "", "  ", "REGISTER", "myPass", " "));

        // then
        verify(commandMapper).mapPartsToCommand(sender, asList("AuthMe", "REGISTER", "myPass"));
    }

    @Test
    void shouldKeepUnicodeArgumentsIntact() {
        // when
        handler.processCommand(sender, asList("authme", "login", "Jörg_ß", "пароль"));

        // then
        verify(commandMapper).mapPartsToCommand(sender, asList("authme", "login", "Jörg_ß", "пароль"));
    }

    @Test
    void shouldTreatNullPartsAsEmptyCommand() {
        // when
        handler.processCommand(sender, null);

        // then
        verify(commandMapper).mapPartsToCommand(sender, Collections.emptyList());
    }

    @Test
    void shouldTreatNullBukkitArgsAsNoArguments() {
        // when
        handler.processCommand(sender, "authme", null);

        // then
        verify(commandMapper).mapPartsToCommand(sender, asList("authme"));
    }

    @Test
    void shouldHandleEmptyBukkitArgs() {
        // when
        handler.processCommand(sender, "authme", new String[0]);

        // then
        verify(commandMapper).mapPartsToCommand(sender, asList("authme"));
    }
}
