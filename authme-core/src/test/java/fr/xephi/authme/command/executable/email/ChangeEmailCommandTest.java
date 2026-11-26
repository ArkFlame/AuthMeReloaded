package fr.xephi.authme.command.executable.email;

import fr.xephi.authme.command.ExecutableCommand;
import fr.xephi.authme.data.VerificationCodeManager;
import fr.xephi.authme.message.MessageKey;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Arrays;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Test for {@link ChangeEmailCommand}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.WARN)
public class ChangeEmailCommandTest extends AbstractEmailCommandTest {

    @InjectMocks
    private ChangeEmailCommand command;

    @Mock
    private VerificationCodeManager codeManager;

    @Override
    protected ExecutableCommand getCommand() {
        return command;
    }

    @Override
    protected MessageKey getUsageMessage() {
        return MessageKey.USAGE_CHANGE_EMAIL;
    }

    @Test
    public void shouldStopIfVerificationIsRequired() {
        // given
        String name = "Testeroni";
        Player player = initPlayerWithName(name);
        given(codeManager.isVerificationRequired(player)).willReturn(true);

        // when
        command.executeCommand(player, Arrays.asList("mail@example.org", "otherMail@example.com"));

        // then
        verify(codeManager).codeExistOrGenerateNew(name);
        verify(commonService).send(player, MessageKey.VERIFICATION_CODE_REQUIRED);
        verifyNoInteractions(management);
    }

    @Test
    public void shouldForwardData() {
        // given
        Player sender = initPlayerWithName("AmATest");
        given(codeManager.isVerificationRequired(sender)).willReturn(false);
        given(validationService.validateEmail("old_mail@example.org")).willReturn(true);
        given(validationService.validateEmail("new.mail@example.org")).willReturn(true);

        // when
        command.executeCommand(sender, Arrays.asList("old_mail@example.org", "new.mail@example.org"));

        // then
        verify(management).performChangeEmail(sender, "old_mail@example.org", "new.mail@example.org");
    }

    @Test
    public void shouldFailForInvalidOldEmail() {
        // given
        Player sender = initPlayerWithName("AmATest");
        given(codeManager.isVerificationRequired(sender)).willReturn(false);
        given(validationService.validateEmail("notanemail")).willReturn(false);

        // when
        command.executeCommand(sender, Arrays.asList("notanemail", "new@example.org"));

        // then
        verifyNoInteractions(management);
        verify(commonService).send(sender, MessageKey.INVALID_OLD_EMAIL);
    }

    @Test
    public void shouldFailForInvalidNewEmail() {
        // given
        Player sender = initPlayerWithName("AmATest");
        given(codeManager.isVerificationRequired(sender)).willReturn(false);
        given(validationService.validateEmail("old@example.org")).willReturn(true);
        given(validationService.validateEmail("notanemail")).willReturn(false);

        // when
        command.executeCommand(sender, Arrays.asList("old@example.org", "notanemail"));

        // then
        verifyNoInteractions(management);
        verify(commonService).send(sender, MessageKey.INVALID_NEW_EMAIL);
    }

    private Player initPlayerWithName(String name) {
        Player player = mock(Player.class);
        when(player.getName()).thenReturn(name);
        return player;
    }
}
