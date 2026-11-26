package fr.xephi.authme.command.executable.email;

import fr.xephi.authme.command.ExecutableCommand;
import fr.xephi.authme.message.MessageKey;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.util.stream.Stream;

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

    @ParameterizedTest(name = "{3}")
    @MethodSource("invalidEmailCases")
    public void shouldRejectInvalidEmail(String email, String confirmation, boolean emailValid,
                                         MessageKey expectedMessage) {
        // given
        Player sender = mock(Player.class);
        given(validationService.validateEmail(email)).willReturn(emailValid);

        // when
        command.executeCommand(sender, Arrays.asList(email, confirmation));

        // then
        verifyNoInteractions(management);
        verify(commonService).send(sender, expectedMessage);
    }

    private static Stream<Arguments> invalidEmailCases() {
        return Stream.of(
            Arguments.of("notanemail", "notanemail", false, MessageKey.INVALID_EMAIL),
            Arguments.of("asdfasdf@example.com", "wrongConf", true, MessageKey.CONFIRM_EMAIL_MESSAGE));
    }
}
