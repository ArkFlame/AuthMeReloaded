package fr.xephi.authme.command.executable.email;

import fr.xephi.authme.command.ExecutableCommand;
import fr.xephi.authme.message.MessageKey;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Arrays;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Test for {@link AddEmailCommand}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.WARN)
public class AddEmailCommandTest extends AbstractEmailCommandTest {

    @InjectMocks
    private AddEmailCommand command;

    @Override
    protected ExecutableCommand getCommand() {
        return command;
    }

    @Override
    protected MessageKey getUsageMessage() {
        return MessageKey.USAGE_ADD_EMAIL;
    }

    @Test
    public void shouldForwardData() {
        // given
        Player sender = mock(Player.class);
        String email = "mail@example.com";
        given(validationService.validateEmail(email)).willReturn(true);

        // when
        command.executeCommand(sender, Arrays.asList(email, email));

        // then
        verify(management).performAddEmail(sender, email);
    }

    @Test
    public void shouldFailForInvalidEmailFormat() {
        // given
        Player sender = mock(Player.class);
        String email = "notanemail";
        given(validationService.validateEmail(email)).willReturn(false);

        // when
        command.executeCommand(sender, Arrays.asList(email, email));

        // then
        verifyNoInteractions(management);
        verify(commonService).send(sender, MessageKey.INVALID_EMAIL);
    }

    @Test
    public void shouldFailForConfirmationMismatch() {
        // given
        Player sender = mock(Player.class);
        String email = "asdfasdf@example.com";
        given(validationService.validateEmail(email)).willReturn(true);

        // when
        command.executeCommand(sender, Arrays.asList(email, "wrongConf"));

        // then
        verifyNoInteractions(management);
        verify(commonService).send(sender, MessageKey.CONFIRM_EMAIL_MESSAGE);
    }
}
