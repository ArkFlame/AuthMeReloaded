package fr.xephi.authme.listener;

import fr.xephi.authme.data.captcha.RegistrationCaptchaManager;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.message.Messages;
import fr.xephi.authme.platform.DialogWindowSpec;
import fr.xephi.authme.platform.PaperDialogHelper;
import fr.xephi.authme.process.register.RegisterSecondaryArgument;
import fr.xephi.authme.process.register.RegistrationType;
import fr.xephi.authme.service.CommonService;
import fr.xephi.authme.service.DialogWindowService;
import fr.xephi.authme.service.PreJoinDialogService;
import fr.xephi.authme.service.ValidationService;
import fr.xephi.authme.settings.properties.RegistrationSettings;
import fr.xephi.authme.settings.properties.RestrictionSettings;
import fr.xephi.authme.util.InternetProtocolUtils;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.dialog.DialogResponseView;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;

/**
 * Validates the answer submitted through a pre-join registration dialog and stores it for the join phase.
 */
final class PreJoinRegisterProcessor {

    private final CommonService commonService;
    private final Messages messages;
    private final DataSource dataSource;
    private final ValidationService validationService;
    private final RegistrationCaptchaManager registrationCaptchaManager;
    private final PreJoinDialogService preJoinDialogService;
    private final DialogWindowService dialogWindowService;
    private final ConcurrentMap<Long, CompletableFuture<String>> pendingResponses;

    PreJoinRegisterProcessor(CommonService commonService, Messages messages, DataSource dataSource,
                             ValidationService validationService,
                             RegistrationCaptchaManager registrationCaptchaManager,
                             PreJoinDialogService preJoinDialogService, DialogWindowService dialogWindowService,
                             ConcurrentMap<Long, CompletableFuture<String>> pendingResponses) {
        this.commonService = commonService;
        this.messages = messages;
        this.dataSource = dataSource;
        this.validationService = validationService;
        this.registrationCaptchaManager = registrationCaptchaManager;
        this.preJoinDialogService = preJoinDialogService;
        this.dialogWindowService = dialogWindowService;
        this.pendingResponses = pendingResponses;
    }

    void storePendingRegistration(PlayerConfigurationConnection connection, long sessionId, String playerName,
                                  DialogResponseView dialogResponseView) {
        if (dialogResponseView == null) {
            PaperDialogSessionSupport.completeResponse(pendingResponses, sessionId, null);
            return;
        }

        // Registration through the dialog must not bypass the registration captcha requirement
        if (registrationCaptchaManager != null && registrationCaptchaManager.isCaptchaRequired(playerName)) {
            String code = registrationCaptchaManager.getCaptchaCodeOrGenerateNew(playerName);
            showRegisterDialogWithError(connection, playerName,
                messages.retrieveSingle(playerName, MessageKey.CAPTCHA_FOR_REGISTRATION_REQUIRED, code));
            return;
        }

        if (exceedsRegistrationLimit(connection, playerName, sessionId)) {
            return;
        }

        RegistrationType registrationType = commonService.getProperty(RegistrationSettings.REGISTRATION_TYPE);
        RegisterSecondaryArgument secondArg = commonService.getProperty(RegistrationSettings.REGISTER_SECOND_ARGUMENT);
        if (registrationType == RegistrationType.EMAIL) {
            storeEmailRegistration(connection, sessionId, playerName, secondArg, dialogResponseView);
        } else {
            storePasswordRegistration(connection, sessionId, playerName, secondArg, dialogResponseView);
        }
    }

    private boolean exceedsRegistrationLimit(PlayerConfigurationConnection connection, String playerName,
                                             long sessionId) {
        InetSocketAddress clientAddress = connection.getClientAddress();
        if (clientAddress == null) {
            return false;
        }
        String ip = clientAddress.getAddress().getHostAddress();
        int maxRegPerIp = commonService.getProperty(RestrictionSettings.MAX_REGISTRATION_PER_IP);
        if (maxRegPerIp <= 0 || InternetProtocolUtils.isLoopbackAddress(ip)) {
            return false;
        }
        List<String> otherAccounts = dataSource.getAllAuthsByIp(ip);
        if (otherAccounts.size() < maxRegPerIp) {
            return false;
        }
        PaperDialogSessionSupport.completeResponse(pendingResponses, sessionId,
            messages.retrieveSingle(playerName, MessageKey.MAX_REGISTER_EXCEEDED,
                Integer.toString(maxRegPerIp), Integer.toString(otherAccounts.size()),
                String.join(", ", otherAccounts)));
        return true;
    }

    private void storeEmailRegistration(PlayerConfigurationConnection connection, long sessionId, String playerName,
                                        RegisterSecondaryArgument secondArg, DialogResponseView view) {
        String email = view.getText("email");
        String confirm = view.getText("confirm");
        if (email == null || email.isBlank() || !validationService.validateEmail(email)) {
            showRegisterDialogWithError(connection, playerName,
                messages.retrieveSingle(playerName, MessageKey.INVALID_EMAIL));
            return;
        }
        if (secondArg == RegisterSecondaryArgument.CONFIRMATION && !email.equals(confirm)) {
            showRegisterDialogWithError(connection, playerName,
                messages.retrieveSingle(playerName, MessageKey.PASSWORD_MATCH_ERROR));
            return;
        }
        preJoinDialogService.storePendingEmailRegistration(sessionId, email);
        PaperDialogSessionSupport.completeResponse(pendingResponses, sessionId, null);
    }

    private void storePasswordRegistration(PlayerConfigurationConnection connection, long sessionId,
                                           String playerName, RegisterSecondaryArgument secondArg,
                                           DialogResponseView view) {
        String password = view.getText("password");
        if (password == null || password.isBlank()) {
            showRegisterDialogWithError(connection, playerName,
                messages.retrieveSingle(playerName, MessageKey.INVALID_PASSWORD_LENGTH));
            return;
        }
        if (secondArg == RegisterSecondaryArgument.CONFIRMATION && !password.equals(view.getText("confirm"))) {
            showRegisterDialogWithError(connection, playerName,
                messages.retrieveSingle(playerName, MessageKey.PASSWORD_MATCH_ERROR));
            return;
        }
        ValidationService.ValidationResult passwordResult = validationService.validatePassword(password, playerName);
        if (passwordResult.hasError()) {
            showRegisterDialogWithError(connection, playerName,
                messages.retrieveSingle(playerName, passwordResult.getMessageKey(), passwordResult.getArgs()));
            return;
        }
        String email = null;
        if (secondArg == RegisterSecondaryArgument.EMAIL_MANDATORY
            || secondArg == RegisterSecondaryArgument.EMAIL_OPTIONAL) {
            email = view.getText("email");
            if (secondArg == RegisterSecondaryArgument.EMAIL_MANDATORY && (email == null || email.isBlank())) {
                showRegisterDialogWithError(connection, playerName,
                    messages.retrieveSingle(playerName, MessageKey.INVALID_EMAIL));
                return;
            }
            if (email != null && !email.isBlank() && !validationService.validateEmail(email)) {
                showRegisterDialogWithError(connection, playerName,
                    messages.retrieveSingle(playerName, MessageKey.INVALID_EMAIL));
                return;
            }
        }
        preJoinDialogService.storePendingPasswordRegistration(sessionId, password, email);
        PaperDialogSessionSupport.completeResponse(pendingResponses, sessionId, null);
    }

    private void showRegisterDialogWithError(PlayerConfigurationConnection connection, String playerName,
                                             String errorMessage) {
        RegistrationType registrationType = commonService.getProperty(RegistrationSettings.REGISTRATION_TYPE);
        RegisterSecondaryArgument secondArg = commonService.getProperty(RegistrationSettings.REGISTER_SECOND_ARGUMENT);
        DialogWindowSpec spec = dialogWindowService.createPreJoinRegisterDialog(playerName, registrationType, secondArg);
        connection.getAudience().showDialog(PaperDialogHelper.createPreJoinRegisterDialog(spec.withBody(errorMessage)));
    }
}
