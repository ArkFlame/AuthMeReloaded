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

import java.util.List;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class SpigotDialogHelperTest {

    @Test
    public void showLoginDialogSendsCorrectCommandTemplate() {
        Player player = mock(Player.class);
        DialogWindowSpec dialog = createDialogSpec("Login", "Password", "Login");

        SpigotDialogHelper.showLoginDialog(player, dialog);

        RunCommandAction action = captureRunCommandAction(player);
        assertThat(action.template(), is("login $(password)"));
    }

    @ParameterizedTest
    @MethodSource("loginDialogs")
    public void showLoginDialogBuildsExpectedActions(List<DialogInputSpec> inputs, boolean stackButtons,
                                                      int expectedColumns) {
        Player player = mock(Player.class);
        DialogWindowSpec dialog = new DialogWindowSpec("Login", inputs, "Login", "Forgot Password?", true,
            false, "email recover $(email)", null, stackButtons);

        SpigotDialogHelper.showLoginDialog(player, dialog);

        MultiActionDialog shown = captureDialog(player);
        assertThat(shown.columns(), is(expectedColumns));
        assertThat(shown.actions().size(), is(2));
        assertThat(((RunCommandAction) shown.actions().get(0).action()).template(), is("login $(password)"));
        assertThat(((RunCommandAction) shown.actions().get(1).action()).template(), is("email recover $(email)"));
    }

    private static Stream<Arguments> loginDialogs() {
        return Stream.of(
            Arguments.of(List.of(new DialogInputSpec("password", "Password", 100)), true, 1),
            Arguments.of(List.of(
                new DialogInputSpec("password", "Password", 100),
                new DialogInputSpec("email", "Recovery Email", 100)), false, 2));
    }

    @Test
    public void showTotpDialogSendsCorrectCommandTemplate() {
        Player player = mock(Player.class);
        DialogWindowSpec dialog = createDialogSpec("2FA", "Code", "Verify");

        SpigotDialogHelper.showTotpDialog(player, dialog);

        RunCommandAction action = captureRunCommandAction(player);
        assertThat(action.template(), is("2fa code $(code)"));
    }

    @ParameterizedTest
    @MethodSource("registerTemplates")
    public void showRegisterDialogSendsExpectedTemplate(String inputLabel, RegistrationType type,
                                                         RegisterSecondaryArgument secondArg,
                                                         String expectedTemplate) {
        Player player = mock(Player.class);
        DialogWindowSpec dialog = createDialogSpec("Register", inputLabel, "Register");
        SpigotDialogHelper.showRegisterDialog(player, type, secondArg, dialog);
        assertThat(captureRunCommandAction(player).template(), is(expectedTemplate));
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

    private static MultiActionDialog captureDialog(Player player) {
        ArgumentCaptor<MultiActionDialog> captor = ArgumentCaptor.forClass(MultiActionDialog.class);
        verify(player).showDialog(captor.capture());
        return captor.getValue();
    }

    private static RunCommandAction captureRunCommandAction(Player player) {
        return (RunCommandAction) captureDialog(player).actions().get(0).action();
    }

    private static DialogWindowSpec createDialogSpec(String title, String inputLabel, String primaryButtonLabel) {
        return new DialogWindowSpec(title,
            List.of(new DialogInputSpec("field", inputLabel, 100)),
            primaryButtonLabel,
            "Cancel",
            false,
            false,
            null);
    }
}
