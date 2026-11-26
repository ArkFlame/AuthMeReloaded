package fr.xephi.authme.command.executable.email;

import fr.xephi.authme.command.ExecutableCommand;
import fr.xephi.authme.data.VerificationCodeManager;
import fr.xephi.authme.message.MessageKey;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.util.stream.Stream;

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

    @ParameterizedTest(name = "{4}")
    @MethodSource("emailCases")
    public void shouldProcessEmail(String oldEmail, String newEmail, boolean oldEmailValid,
                                   boolean newEmailValid, MessageKey expectedMessage) {
        // given
        Player sender = initPlayerWithName("AmATest");
        given(codeManager.isVerificationRequired(sender)).willReturn(false);
        given(validationService.validateEmail(oldEmail)).willReturn(oldEmailValid);
        if (oldEmailValid) {
            given(validationService.validateEmail(newEmail)).willReturn(newEmailValid);
        }

        // when
        command.executeCommand(sender, Arrays.asList(oldEmail, newEmail));

        // then
        if (expectedMessage == null) {
            verify(management).performChangeEmail(sender, oldEmail, newEmail);
        } else {
            verifyNoInteractions(management);
            verify(commonService).send(sender, expectedMessage);
        }
    }

    private static Stream<Arguments> emailCases() {
        return Stream.of(
            Arguments.of("old_mail@example.org", "new.mail@example.org", true, true, null),
            Arguments.of("notanemail", "new@example.org", false, false, MessageKey.INVALID_OLD_EMAIL),
            Arguments.of("old@example.org", "notanemail", true, false, MessageKey.INVALID_NEW_EMAIL));
    }

    private Player initPlayerWithName(String name) {
        Player player = mock(Player.class);
        when(player.getName()).thenReturn(name);
        return player;
    }
}
