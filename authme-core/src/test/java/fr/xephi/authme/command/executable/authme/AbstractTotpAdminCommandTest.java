package fr.xephi.authme.command.executable.authme;

import fr.xephi.authme.TestHelper;
import fr.xephi.authme.command.ExecutableCommand;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.message.Messages;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.verify;

/**
 * Shared tests for admin commands that look up a user's two-factor authentication status.
 */
@ExtendWith(MockitoExtension.class)
abstract class AbstractTotpAdminCommandTest {

    @Mock
    protected DataSource dataSource;
    @Mock
    protected Messages messages;

    @BeforeAll
    static void initLogger() {
        TestHelper.setupLogger();
    }

    @Test
    void shouldHandleUnknownUser() {
        // given
        CommandSender sender = mock(CommandSender.class);
        given(dataSource.getAuth("user")).willReturn(null);

        // when
        getCommand().executeCommand(sender, Collections.singletonList("user"));

        // then
        verify(messages).send(sender, MessageKey.UNKNOWN_USER);
        verify(dataSource, only()).getAuth("user");
    }

    /** @return the command under test */
    protected abstract ExecutableCommand getCommand();
}
