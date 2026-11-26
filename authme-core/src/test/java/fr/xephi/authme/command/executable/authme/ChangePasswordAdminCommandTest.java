package fr.xephi.authme.command.executable.authme;

import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.process.Management;
import fr.xephi.authme.service.CommonService;
import fr.xephi.authme.service.ValidationService;
import fr.xephi.authme.service.ValidationService.ValidationResult;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Test for {@link ChangePasswordAdminCommand}.
 */
@ExtendWith(MockitoExtension.class)
class ChangePasswordAdminCommandTest {

    private static final String NAME = "theUser";
    private static final String PASS = "newPassword";

    @InjectMocks
    private ChangePasswordAdminCommand command;

    @Mock
    private CommonService commonService;

    @Mock
    private ValidationService validationService;

    @Mock
    private Management management;

    @Test
    void shouldForwardRequestToManagement() {
        CommandSender sender = executeWithPassword(new ValidationResult());

        verify(validationService).validatePassword(PASS, NAME);
        verify(management).performPasswordChangeAsAdmin(sender, NAME, PASS);
    }

    @Test
    void shouldSendErrorToCommandSender() {
        CommandSender sender = executeWithPassword(
            new ValidationResult(MessageKey.INVALID_PASSWORD_LENGTH, "7"));

        verify(validationService).validatePassword(PASS, NAME);
        verify(commonService).send(sender, MessageKey.INVALID_PASSWORD_LENGTH, "7");
        verifyNoInteractions(management);
    }

    private CommandSender executeWithPassword(ValidationResult result) {
        given(validationService.validatePassword(PASS, NAME)).willReturn(result);
        CommandSender sender = mock(CommandSender.class);
        command.executeCommand(sender, Arrays.asList(NAME, PASS));
        return sender;
    }
}
