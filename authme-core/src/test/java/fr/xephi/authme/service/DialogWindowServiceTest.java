package fr.xephi.authme.service;

import fr.xephi.authme.mail.EmailService;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.message.Messages;
import fr.xephi.authme.platform.DialogWindowSpec;
import fr.xephi.authme.process.register.RegisterSecondaryArgument;
import fr.xephi.authme.process.register.RegistrationType;
import fr.xephi.authme.settings.properties.RegistrationSettings;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * Test for {@link DialogWindowService}.
 */
@ExtendWith(MockitoExtension.class)
class DialogWindowServiceTest {

    @InjectMocks
    private DialogWindowService dialogWindowService;

    @Mock
    private CommonService commonService;

    @Mock
    private Messages messages;

    @Mock
    private EmailService emailService;

    private Player mockPlayerWithRetrievedMessages() {
        Player player = mock(Player.class);
        given(commonService.retrieveSingleMessage(eq(player), any(MessageKey.class)))
            .willAnswer(invocation -> invocation.getArgument(1, MessageKey.class).getKey());
        return player;
    }

    private void givenPreJoinMessages() {
        given(messages.retrieveSingle(eq("Bobby"), any(MessageKey.class)))
            .willAnswer(invocation -> invocation.getArgument(1, MessageKey.class).getKey());
    }

    private void givenDialogStyle(boolean showBody, boolean stackedButtons) {
        given(commonService.getProperty(RegistrationSettings.DIALOG_SHOW_BODY)).willReturn(showBody);
        given(commonService.getProperty(RegistrationSettings.DIALOG_STACK_BUTTONS_VERTICALLY)).willReturn(stackedButtons);
    }

    @ParameterizedTest
    @CsvSource({"true, true, true", "true, false, false", "false, true, false"})
    void shouldShowRecoveryButtonOnlyWhenEnabledAndEmailConfigured(boolean showForgotPasswordButton,
                                                                   boolean emailConfigured,
                                                                   boolean showBody) {
        // given
        Player player = mockPlayerWithRetrievedMessages();
        given(commonService.getProperty(RegistrationSettings.DIALOG_SHOW_FORGOT_PASSWORD_BUTTON)).willReturn(showForgotPasswordButton);
        givenDialogStyle(showBody, false);
        if (showForgotPasswordButton) {
            given(emailService.hasAllInformation()).willReturn(emailConfigured);
        }

        // when
        DialogWindowSpec dialog = dialogWindowService.createLoginDialog(player);

        // then — recovery appears only when the flag is enabled and email is configured
        boolean recovery = showForgotPasswordButton && emailConfigured;
        assertThat(dialog.title(), is("dialog.login.title"));
        assertThat(dialog.primaryButtonLabel(), is("dialog.login.button"));
        assertThat(dialog.secondaryButtonLabel(), is(recovery ? "dialog.login.recovery_button" : "dialog.button.cancel"));
        assertThat(dialog.showSecondaryButton(), is(recovery));
        assertThat(dialog.secondaryButtonCommand(), is(recovery ? "email recover $(email)" : null));
        assertThat(dialog.canCloseWithEscape(), is(false));
        assertThat(dialog.body(), is(showBody ? "dialog.login.body" : null));
        assertThat(dialog.inputs().stream().map(input -> input.id()).toList(),
            contains(recovery ? new String[]{"password", "email"} : new String[]{"password"}));
    }

    @ParameterizedTest
    @CsvSource({"EMAIL_MANDATORY, email, password", "EMAIL_OPTIONAL, password, email"})
    void shouldBuildPostJoinRegisterDialogWithInputsOrderedByEmailFlow(String secondArgument,
                                                                      String firstInput,
                                                                      String secondInput) {
        // given
        Player player = mockPlayerWithRetrievedMessages();
        givenDialogStyle(false, false);

        // when
        DialogWindowSpec dialog = dialogWindowService.createRegisterDialog(
            player, RegistrationType.PASSWORD, RegisterSecondaryArgument.valueOf(secondArgument));

        // then
        assertThat(dialog.title(), is("dialog.register.title"));
        assertThat(dialog.primaryButtonLabel(), is("dialog.register.button"));
        assertThat(dialog.showSecondaryButton(), is(false));
        assertThat(dialog.canCloseWithEscape(), is(false));
        assertThat(dialog.body(), is(nullValue()));
        assertThat(dialog.inputs().stream().map(input -> input.id()).toList(), contains(firstInput, secondInput));
    }

    @Test
    void shouldBuildPreJoinLoginDialogWithCancelButtonWhenEmailServiceNotConfigured() {
        // given
        givenPreJoinMessages();
        given(commonService.getProperty(RegistrationSettings.DIALOG_SHOW_FORGOT_PASSWORD_BUTTON)).willReturn(true);
        given(commonService.getProperty(RegistrationSettings.PRE_JOIN_DIALOG_SHOW_CANCEL_BUTTON)).willReturn(true);
        given(commonService.getProperty(RegistrationSettings.PRE_JOIN_DIALOG_ALLOW_CLOSE_WITH_ESCAPE)).willReturn(true);
        givenDialogStyle(true, false);
        given(emailService.hasAllInformation()).willReturn(false);

        // when — email not configured: no recovery, cancel button shown instead
        DialogWindowSpec dialog = dialogWindowService.createPreJoinLoginDialog("Bobby");

        // then
        assertThat(dialog.title(), is("dialog.login.title"));
        assertThat(dialog.primaryButtonLabel(), is("dialog.login.button"));
        assertThat(dialog.secondaryButtonLabel(), is("dialog.button.cancel"));
        assertThat(dialog.showSecondaryButton(), is(true));
        assertThat(dialog.canCloseWithEscape(), is(true));
        assertThat(dialog.body(), is("dialog.login.body"));
        assertThat(dialog.buttonColumns(), is(2));
        assertThat(dialog.inputs().stream().map(input -> input.id()).toList(), contains("password"));
        assertThat(dialog.secondaryButtonCommand(), is(nullValue()));
    }

    @Test
    void shouldBuildPreJoinLoginDialogWithRecoveryButtonWhenEmailServiceConfigured() {
        // given
        givenPreJoinMessages();
        given(commonService.getProperty(RegistrationSettings.DIALOG_SHOW_FORGOT_PASSWORD_BUTTON)).willReturn(true);
        givenDialogStyle(false, false);
        given(emailService.hasAllInformation()).willReturn(true);

        // when — email configured: recovery button shown, cancel suppressed, email field present
        DialogWindowSpec dialog = dialogWindowService.createPreJoinLoginDialog("Bobby");

        // then — email field is on a separate recovery dialog now; only password on login dialog
        assertThat(dialog.secondaryButtonLabel(), is("dialog.login.recovery_button"));
        assertThat(dialog.showSecondaryButton(), is(true));
        assertThat(dialog.canCloseWithEscape(), is(false));
        assertThat(dialog.secondaryButtonCommand(), is(notNullValue()));
        assertThat(dialog.inputs().stream().map(input -> input.id()).toList(), contains("password"));
    }

    @Test
    void shouldKeepStackedButtonsInPreJoinRegisterDialogWhenBodyIsReplaced() {
        // given
        givenPreJoinMessages();
        given(commonService.getProperty(RegistrationSettings.PRE_JOIN_DIALOG_SHOW_CANCEL_BUTTON)).willReturn(true);
        given(commonService.getProperty(RegistrationSettings.PRE_JOIN_DIALOG_ALLOW_CLOSE_WITH_ESCAPE)).willReturn(true);
        givenDialogStyle(false, true);

        // when
        DialogWindowSpec dialog = dialogWindowService.createPreJoinRegisterDialog(
            "Bobby", RegistrationType.PASSWORD, RegisterSecondaryArgument.CONFIRMATION).withBody("error");

        // then
        assertThat(dialog.body(), is("error"));
        assertThat(dialog.showSecondaryButton(), is(true));
        assertThat(dialog.buttonColumns(), is(1));
    }
}
