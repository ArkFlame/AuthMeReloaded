package fr.xephi.authme.command.executable.authme;

import fr.xephi.authme.command.ExecutableCommand;
import fr.xephi.authme.data.auth.PlayerAuth;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.hamcrest.MockitoHamcrest.argThat;

/**
 * Test for {@link TotpViewStatusCommand}.
 */
@ExtendWith(MockitoExtension.class)
class TotpViewStatusCommandTest extends AbstractTotpAdminCommandTest {

    @InjectMocks
    private TotpViewStatusCommand command;

    @Override
    protected ExecutableCommand getCommand() {
        return command;
    }

    @Test
    void shouldInformForUserWithoutTotp() {
        // given
        CommandSender sender = mock(CommandSender.class);
        PlayerAuth auth = PlayerAuth.builder()
            .name("billy")
            .totpKey(null)
            .build();
        given(dataSource.getAuth("Billy")).willReturn(auth);

        // when
        command.executeCommand(sender, Collections.singletonList("Billy"));

        // then
        verify(sender).sendMessage(argThat(containsString("'Billy' does NOT have two-factor auth enabled")));
    }

    @Test
    void shouldInformForUserWithTotpEnabled() {
        // given
        CommandSender sender = mock(CommandSender.class);
        PlayerAuth auth = PlayerAuth.builder()
            .name("billy")
            .totpKey("92841575")
            .build();
        given(dataSource.getAuth("Billy")).willReturn(auth);

        // when
        command.executeCommand(sender, Collections.singletonList("Billy"));

        // then
        verify(sender).sendMessage(argThat(containsString("'Billy' has enabled two-factor authentication")));
    }
}
