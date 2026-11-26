package fr.xephi.authme.command.executable.changepassword;

import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import fr.xephi.authme.data.VerificationCodeManager;
import fr.xephi.authme.data.auth.PlayerCache;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.message.Messages;
import fr.xephi.authme.process.Management;
import fr.xephi.authme.service.CommonService;
import fr.xephi.authme.service.ValidationService;
import fr.xephi.authme.service.ValidationService.ValidationResult;
import fr.xephi.authme.settings.properties.SecuritySettings;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.Arrays;
import java.util.Collections;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test for {@link ChangePasswordCommand}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.WARN)
public class ChangePasswordCommandTest {

    @InjectMocks
    private ChangePasswordCommand command;

    @Mock
    private CommonService commonService;

    @Mock
    private PlayerCache playerCache;

    @Mock
    private VerificationCodeManager codeManager;

    @Mock
    private ValidationService validationService;

    @Mock
    private Management management;

    @Mock
    private Messages messages;

    @BeforeEach
    public void requireVerificationByDefault() {
        given(commonService.getProperty(SecuritySettings.CHANGE_PASSWORD_EMAIL_VERIFICATION_REQUIRED)).willReturn(true);
    }

    @Test
    public void shouldRejectNonPlayerSender() {
        // given
        CommandSender sender = mock(CommandSender.class);

        // when
        command.executeCommand(sender, Collections.emptyList());

        // then
        verify(messages).send(sender, MessageKey.PLAYER_COMMAND_ONLY_WITH_ALTERNATIVE, "/authme password <playername> <password>");
    }

    @Test
    public void shouldRejectNotLoggedInPlayer() {
        // given
        CommandSender sender = initPlayerWithName("name", false);

        // when
        command.executeCommand(sender, Arrays.asList("pass", "pass"));

        // then
        verify(commonService).send(sender, MessageKey.NOT_LOGGED_IN);
    }

    @Test
    public void shouldRejectInvalidPassword() {
        // given
        Player sender = initPlayerWithName("abc12", true);
        String password = "newPW";
        given(validationService.validatePassword(password, "abc12")).willReturn(new ValidationResult(MessageKey.INVALID_PASSWORD_LENGTH));

        // when
        command.executeCommand(sender, Arrays.asList("tester", password));

        // then
        verify(validationService).validatePassword(password, "abc12");
        verify(commonService).send(sender, MessageKey.INVALID_PASSWORD_LENGTH, new String[0]);
        verify(codeManager).isVerificationRequired(sender);
    }

    @Test
    public void shouldForwardTheDataForValidPassword() {
        // given
        String oldPass = "oldpass";
        String newPass = "abc123";
        Player player = initPlayerWithName("parker", true);
        given(validationService.validatePassword("abc123", "parker")).willReturn(new ValidationResult());

        // when
        command.executeCommand(player, Arrays.asList(oldPass, newPass));

        // then
        verify(validationService).validatePassword(newPass, "parker");
        verify(commonService, never()).send(eq(player), any(MessageKey.class));
        verify(management).performPasswordChange(player, oldPass, newPass);
        verify(codeManager).isVerificationRequired(player);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void shouldHandleUnverifiedPlayerAccordingToVerificationSetting(boolean verificationRequired) {
        // given
        Player player = initPlayerWithName("unverified", true);
        given(commonService.getProperty(SecuritySettings.CHANGE_PASSWORD_EMAIL_VERIFICATION_REQUIRED)).willReturn(verificationRequired);
        given(codeManager.isVerificationRequired(player)).willReturn(true);
        if (!verificationRequired) {
            given(validationService.validatePassword("newPass", "unverified")).willReturn(new ValidationResult());
        }

        // when
        command.executeCommand(player, Arrays.asList("oldPass", "newPass"));

        // then
        if (verificationRequired) {
            verify(codeManager).codeExistOrGenerateNew("unverified");
            verify(commonService).send(player, MessageKey.VERIFICATION_CODE_REQUIRED);
            verify(validationService, never()).validatePassword("newPass", "unverified");
            verify(management, never()).performPasswordChange(player, "oldPass", "newPass");
        } else {
            verify(codeManager, never()).codeExistOrGenerateNew(org.mockito.ArgumentMatchers.anyString());
            verify(management).performPasswordChange(player, "oldPass", "newPass");
        }
    }

    @Test
    public void shouldDefineArgumentMismatchMessage() {
        // given / when / then
        assertThat(command.getArgumentsMismatchMessage(), equalTo(MessageKey.USAGE_CHANGE_PASSWORD));
    }

    private Player initPlayerWithName(String name, boolean loggedIn) {
        Player player = mock(Player.class);
        when(player.getName()).thenReturn(name);
        when(playerCache.isAuthenticated(name)).thenReturn(loggedIn);
        return player;
    }
}
