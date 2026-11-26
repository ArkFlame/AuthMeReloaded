package fr.xephi.authme.platform;

import fr.xephi.authme.process.register.RegisterSecondaryArgument;
import fr.xephi.authme.process.register.RegistrationType;
import net.md_5.bungee.api.dialog.MultiActionDialog;
import net.md_5.bungee.api.dialog.action.RunCommandAction;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class SpigotDialogHelperTest {

    @Test
    public void showLoginDialogSendsCorrectCommandTemplate() {
        // given
        Player player = mock(Player.class);
        DialogWindowSpec dialog = createDialogSpec("Login", "Password", "Login");

        // when
        SpigotDialogHelper.showLoginDialog(player, dialog);

        // then
        RunCommandAction action = captureRunCommandAction(player);
        assertThat(action.template(), is("login $(password)"));
    }

    @Test
    public void showLoginDialogStacksButtonsWhenEnabled() {
        // given
        Player player = mock(Player.class);
        DialogWindowSpec dialog = new DialogWindowSpec("Login",
            java.util.List.of(new DialogInputSpec("password", "Password", 100)),
            "Login",
            "Forgot Password?",
            true,
            false,
            "email recover $(email)",
            null,
            true);

        // when
        SpigotDialogHelper.showLoginDialog(player, dialog);

        // then
        ArgumentCaptor<MultiActionDialog> captor = ArgumentCaptor.forClass(MultiActionDialog.class);
        verify(player).showDialog(captor.capture());
        assertThat(captor.getValue().columns(), is(1));
    }

    @Test
    public void showLoginDialogIncludesForgotPasswordButtonWhenSpecified() {
        // given
        Player player = mock(Player.class);
        DialogWindowSpec dialog = new DialogWindowSpec("Login",
            java.util.List.of(
                new DialogInputSpec("password", "Password", 100),
                new DialogInputSpec("email", "Recovery Email", 100)),
            "Login",
            "Forgot Password?",
            true,
            false,
            "email recover $(email)");

        // when
        SpigotDialogHelper.showLoginDialog(player, dialog);

        // then
        ArgumentCaptor<MultiActionDialog> captor = ArgumentCaptor.forClass(MultiActionDialog.class);
        verify(player).showDialog(captor.capture());
        assertThat(captor.getValue().actions().size(), is(2));
        assertThat(((RunCommandAction) captor.getValue().actions().get(0).action()).template(), is("login $(password)"));
        assertThat(((RunCommandAction) captor.getValue().actions().get(1).action()).template(), is("email recover $(email)"));
    }

    @Test
    public void showTotpDialogSendsCorrectCommandTemplate() {
        // given
        Player player = mock(Player.class);
        DialogWindowSpec dialog = createDialogSpec("2FA", "Code", "Verify");

        // when
        SpigotDialogHelper.showTotpDialog(player, dialog);

        // then
        RunCommandAction action = captureRunCommandAction(player);
        assertThat(action.template(), is("2fa code $(code)"));
    }

    @ParameterizedTest
    @MethodSource("registerTemplates")
    public void showRegisterDialogSendsExpectedTemplate(String inputLabel, RegistrationType type,
                                                         RegisterSecondaryArgument secondArg,
                                                         String expectedTemplate) {
        assertRegisterTemplate(inputLabel, type, secondArg, expectedTemplate);
    }

    private static Stream<Arguments> registerTemplates() {
        return Stream.of(
            Arguments.of("Password", RegistrationType.PASSWORD, RegisterSecondaryArgument.NONE,
                "register $(password)"),
            Arguments.of("Password", RegistrationType.PASSWORD, RegisterSecondaryArgument.CONFIRMATION,
                "register $(password) $(confirm)"),
            Arguments.of("Password", RegistrationType.PASSWORD, RegisterSecondaryArgument.EMAIL_MANDATORY,
                "register $(password) $(email)"),
            Arguments.of("Password", RegistrationType.PASSWORD, RegisterSecondaryArgument.EMAIL_OPTIONAL,
                "register $(password) $(email)"),
            Arguments.of("Email", RegistrationType.EMAIL, RegisterSecondaryArgument.NONE,
                "register $(email)"),
            Arguments.of("Email", RegistrationType.EMAIL, RegisterSecondaryArgument.CONFIRMATION,
                "register $(email) $(confirm)"));
    }

    private static void assertRegisterTemplate(String inputLabel, RegistrationType type,
                                                RegisterSecondaryArgument secondArg, String expectedTemplate) {
        Player player = mock(Player.class);
        DialogWindowSpec dialog = createDialogSpec("Register", inputLabel, "Register");
        SpigotDialogHelper.showRegisterDialog(player, type, secondArg, dialog);
        assertThat(captureRunCommandAction(player).template(), is(expectedTemplate));
    }

    private static RunCommandAction captureRunCommandAction(Player player) {
        ArgumentCaptor<MultiActionDialog> captor = ArgumentCaptor.forClass(MultiActionDialog.class);
        verify(player).showDialog(captor.capture());
        return (RunCommandAction) captor.getValue().actions().get(0).action();
    }

    private static DialogWindowSpec createDialogSpec(String title, String inputLabel, String primaryButtonLabel) {
        return new DialogWindowSpec(title,
            java.util.List.of(new DialogInputSpec("field", inputLabel, 100)),
            primaryButtonLabel,
            "Cancel",
            false,
            false,
            null);
    }
}
