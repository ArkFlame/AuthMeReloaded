package fr.xephi.authme.command.executable.email;

import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import fr.xephi.authme.TestHelper;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.security.PasswordSecurity;
import fr.xephi.authme.security.crypts.HashedPassword;
import fr.xephi.authme.service.CommonService;
import fr.xephi.authme.service.PasswordRecoveryService;
import fr.xephi.authme.service.ValidationService;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.Collections;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Tests for {@link EmailSetPasswordCommand}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.WARN)
public class EmailSetPasswordCommandTest {

    @InjectMocks
    private EmailSetPasswordCommand command;

    @Mock
    private DataSource dataSource;

    @Mock
    private CommonService commonService;

    @Mock
    private PasswordRecoveryService recoveryService;

    @Mock
    private PasswordSecurity passwordSecurity;

    @Mock
    private ValidationService validationService;

    @Mock
    private Player player;

    @BeforeAll
    public static void setUpLogger() {
        TestHelper.setupLogger();
    }

    @Test
    public void shouldChangePassword() {
        // given
        HashedPassword hashedPassword = givenPasswordChangeSetup("Jerry", "abc123", true);

        // when
        command.runCommand(player, Collections.singletonList("abc123"));

        // then
        verify(validationService).validatePassword("abc123", "Jerry");
        verify(dataSource).updatePassword("Jerry", hashedPassword);
        verify(recoveryService).removeFromSuccessfulRecovery(player);
        verify(commonService).send(player, MessageKey.PASSWORD_CHANGED_SUCCESS);
    }

    @Test
    public void shouldReportErrorWhenDatabaseUpdateFails() {
        // given
        HashedPassword hashedPassword = givenPasswordChangeSetup("Jerry", "abc123", false);

        // when
        command.runCommand(player, Collections.singletonList("abc123"));

        // then
        verify(dataSource).updatePassword("Jerry", hashedPassword);
        verify(recoveryService).canChangePassword(player);
        verifyNoMoreInteractions(recoveryService);
        verify(commonService).send(player, MessageKey.ERROR);
        verifyNoMoreInteractions(commonService);
    }

    @Test
    public void shouldRejectInvalidPassword() {
        // given
        String name = givenPlayerCanChangePassword("Morgan");
        String password = "newPW";
        given(validationService.validatePassword(password, name))
            .willReturn(new ValidationService.ValidationResult(MessageKey.INVALID_PASSWORD_LENGTH));

        // when
        command.executeCommand(player, Collections.singletonList(password));

        // then
        verify(validationService).validatePassword(password, name);
        verify(commonService).send(player, MessageKey.INVALID_PASSWORD_LENGTH, new String[0]);
    }

    @Test
    public void shouldDoNothingCantChangePass() {
        // when
        command.runCommand(player, Collections.singletonList("abc123"));

        // then
        verifyNoInteractions(validationService, dataSource);
        verify(commonService).send(player, MessageKey.CHANGE_PASSWORD_EXPIRED);
    }

    private String givenPlayerCanChangePassword(String name) {
        given(player.getName()).willReturn(name);
        given(recoveryService.canChangePassword(player)).willReturn(true);
        return name;
    }

    private HashedPassword givenPasswordChangeSetup(String name, String password, boolean updateSucceeds) {
        givenPlayerCanChangePassword(name);
        HashedPassword hashedPassword = new HashedPassword("hash");
        given(passwordSecurity.computeHash(password, name)).willReturn(hashedPassword);
        given(validationService.validatePassword(password, name)).willReturn(new ValidationService.ValidationResult());
        given(dataSource.updatePassword(name, hashedPassword)).willReturn(updateSucceeds);
        return hashedPassword;
    }
}
