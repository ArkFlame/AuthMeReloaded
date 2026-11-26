package fr.xephi.authme.listener;

import com.destroystokyo.paper.profile.PlayerProfile;
import fr.xephi.authme.data.captcha.RegistrationCaptchaManager;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.message.Messages;
import fr.xephi.authme.platform.DialogInputSpec;
import fr.xephi.authme.platform.DialogWindowSpec;
import fr.xephi.authme.platform.PaperDialogActionKeys;
import fr.xephi.authme.platform.PaperDialogHelper;
import fr.xephi.authme.process.register.RegisterSecondaryArgument;
import fr.xephi.authme.process.register.RegistrationType;
import fr.xephi.authme.service.CommonService;
import fr.xephi.authme.service.DialogWindowService;
import fr.xephi.authme.service.PreJoinDialogService;
import fr.xephi.authme.service.ValidationService;
import fr.xephi.authme.settings.properties.RegistrationSettings;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import net.kyori.adventure.audience.Audience;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** Registration dialogs must honour the registration captcha requirement. */
public class PaperDialogFlowListenerIssue3088Test {

    private static final long SESSION_ID = 42L;

    @Test
    public void shouldRejectRegisterDialogSubmissionWhileCaptchaIsRequired() throws Exception {
        PaperDialogFlowListener listener = new PaperDialogFlowListener();
        CommonService commonService = mock(CommonService.class);
        Messages messages = mock(Messages.class);
        ValidationService validationService = mock(ValidationService.class);
        DialogWindowService dialogWindowService = mock(DialogWindowService.class);
        PreJoinDialogService preJoinDialogService = mock(PreJoinDialogService.class);
        RegistrationCaptchaManager captchaManager = mock(RegistrationCaptchaManager.class);
        Audience audience = mock(Audience.class);
        setField(listener, "commonService", commonService);
        setField(listener, "messages", messages);
        setField(listener, "validationService", validationService);
        setField(listener, "dialogWindowService", dialogWindowService);
        setField(listener, "preJoinDialogService", preJoinDialogService);
        setField(listener, "registrationCaptchaManager", captchaManager);

        given(commonService.getProperty(RegistrationSettings.REGISTRATION_TYPE))
            .willReturn(RegistrationType.PASSWORD);
        given(commonService.getProperty(RegistrationSettings.REGISTER_SECOND_ARGUMENT))
            .willReturn(RegisterSecondaryArgument.CONFIRMATION);
        given(captchaManager.isCaptchaRequired("Bobby")).willReturn(true);
        given(captchaManager.getCaptchaCodeOrGenerateNew("Bobby")).willReturn("A1B2C");
        given(messages.retrieveSingle("Bobby", MessageKey.CAPTCHA_FOR_REGISTRATION_REQUIRED, "A1B2C"))
            .willReturn("Captcha required: A1B2C");
        given(dialogWindowService.createPreJoinRegisterDialog("Bobby", RegistrationType.PASSWORD,
            RegisterSecondaryArgument.CONFIRMATION)).willReturn(new DialogWindowSpec("Register",
            List.of(new DialogInputSpec("password", "Password", 100)), "Register", "Cancel", false, false, null));

        CompletableFuture<String> future = new CompletableFuture<>();
        PlayerConfigurationConnection connection = mockConnection(audience);
        connectionSessions(listener).put(connection, SESSION_ID);
        pendingResponses(listener).put(SESSION_ID, future);

        DialogResponseView responseView = mock(DialogResponseView.class);
        given(responseView.getText("password")).willReturn("securePassword");
        given(responseView.getText("confirm")).willReturn("securePassword");
        PlayerCustomClickEvent event = mock(PlayerCustomClickEvent.class);
        given(event.getCommonConnection()).willReturn(connection);
        given(event.getIdentifier()).willReturn(PaperDialogActionKeys.PRE_JOIN_REGISTER_SUBMIT);
        given(event.getDialogResponseView()).willReturn(responseView);

        try (var helperStatic = mockStatic(PaperDialogHelper.class)) {
            helperStatic.when(() -> PaperDialogHelper.createPreJoinRegisterDialog(any())).thenReturn(null);

            listener.onPlayerCustomClick(event);

            assertThat("registration must not proceed while captcha is required", future.isDone(), is(false));
            verify(preJoinDialogService, never()).storePendingPasswordRegistration(anyLong(), any(), any());
            verify(validationService, never()).validatePassword(any(), any());
            helperStatic.verify(() -> PaperDialogHelper.createPreJoinRegisterDialog(
                argThat(spec -> spec != null && "Captcha required: A1B2C".equals(spec.body()))));
        }
    }

    private static PlayerConfigurationConnection mockConnection(Audience audience) {
        PlayerProfile profile = mock(PlayerProfile.class);
        given(profile.getName()).willReturn("Bobby");
        PlayerConfigurationConnection connection = mock(PlayerConfigurationConnection.class);
        given(connection.getProfile()).willReturn(profile);
        given(connection.getAudience()).willReturn(audience);
        return connection;
    }

    @SuppressWarnings("unchecked")
    private static ConcurrentMap<PlayerConfigurationConnection, Long> connectionSessions(
        PaperDialogFlowListener listener) throws Exception {
        Field field = PaperDialogFlowListener.class.getDeclaredField("connectionSessions");
        field.setAccessible(true);
        return (ConcurrentMap<PlayerConfigurationConnection, Long>) field.get(listener);
    }

    @SuppressWarnings("unchecked")
    private static ConcurrentMap<Long, CompletableFuture<String>> pendingResponses(
        PaperDialogFlowListener listener) throws Exception {
        Field field = PaperDialogFlowListener.class.getDeclaredField("pendingRegisterResponses");
        field.setAccessible(true);
        return (ConcurrentMap<Long, CompletableFuture<String>>) field.get(listener);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
