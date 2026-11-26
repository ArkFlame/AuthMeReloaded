package fr.xephi.authme.listener;

import fr.xephi.authme.data.TempbanManager;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.data.captcha.LoginCaptchaManager;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.message.Messages;
import fr.xephi.authme.platform.DialogWindowSpec;
import fr.xephi.authme.platform.PaperDialogHelper;
import fr.xephi.authme.security.PasswordSecurity;
import fr.xephi.authme.service.BukkitService;
import fr.xephi.authme.service.CommonService;
import fr.xephi.authme.service.DialogWindowService;
import fr.xephi.authme.service.PreJoinDialogService;
import fr.xephi.authme.settings.properties.RegistrationSettings;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.dialog.DialogResponseView;

import java.net.InetSocketAddress;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;

/**
 * Processes the answer submitted through a pre-join login or recovery dialog.
 */
final class PreJoinLoginProcessor {

    private final Messages messages;
    private final LoginCaptchaManager loginCaptchaManager;
    private final TempbanManager tempbanManager;
    private final DataSource dataSource;
    private final PasswordSecurity passwordSecurity;
    private final BukkitService bukkitService;
    private final PreJoinDialogService preJoinDialogService;
    private final DialogWindowService dialogWindowService;
    private final CommonService commonService;
    private final ConcurrentMap<Long, CompletableFuture<String>> pendingResponses;

    PreJoinLoginProcessor(Messages messages, LoginCaptchaManager loginCaptchaManager, TempbanManager tempbanManager,
                          DataSource dataSource, PasswordSecurity passwordSecurity, BukkitService bukkitService,
                          PreJoinDialogService preJoinDialogService, DialogWindowService dialogWindowService,
                          CommonService commonService,
                          ConcurrentMap<Long, CompletableFuture<String>> pendingResponses) {
        this.messages = messages;
        this.loginCaptchaManager = loginCaptchaManager;
        this.tempbanManager = tempbanManager;
        this.dataSource = dataSource;
        this.passwordSecurity = passwordSecurity;
        this.bukkitService = bukkitService;
        this.preJoinDialogService = preJoinDialogService;
        this.dialogWindowService = dialogWindowService;
        this.commonService = commonService;
        this.pendingResponses = pendingResponses;
    }

    void processLogin(long sessionId, PlayerConfigurationConnection connection, String playerName,
                      DialogResponseView dialogResponseView) {
        String password = dialogResponseView == null ? null : dialogResponseView.getText("password");
        if (password == null || password.isBlank()) {
            PaperDialogSessionSupport.completeResponse(pendingResponses, sessionId,
                messages.retrieveSingle(playerName, MessageKey.LOGIN_TIMEOUT_ERROR));
            return;
        }

        String normalizedName = playerName.toLowerCase(Locale.ROOT);
        InetSocketAddress clientAddress = connection.getClientAddress();
        String ip = clientAddress == null ? null : clientAddress.getAddress().getHostAddress();
        if (loginCaptchaManager.isCaptchaRequired(normalizedName) || (ip != null && tempbanManager.shouldTempban(ip))) {
            PaperDialogSessionSupport.completeResponse(pendingResponses, sessionId, null);
            return;
        }

        bukkitService.runTaskAsynchronously(() -> {
            PlayerAuth auth = dataSource.getAuth(normalizedName);
            if (auth == null) {
                PaperDialogSessionSupport.completeResponse(pendingResponses, sessionId,
                    messages.retrieveSingle(playerName, MessageKey.UNKNOWN_USER));
                return;
            }

            loginCaptchaManager.increaseLoginFailureCount(normalizedName);
            if (ip != null) {
                tempbanManager.increaseCount(ip, normalizedName);
            }

            if (passwordSecurity.comparePassword(password, auth.getPassword(), playerName)) {
                loginCaptchaManager.resetLoginFailureCount(normalizedName);
                if (ip != null) {
                    tempbanManager.resetCount(ip, normalizedName);
                }
                preJoinDialogService.storePendingLoginPassword(sessionId, password);
                PaperDialogSessionSupport.completeResponse(pendingResponses, sessionId, null);
            } else {
                PaperDialogSessionSupport.completeResponse(pendingResponses, sessionId,
                    messages.retrieveSingle(playerName, MessageKey.WRONG_PASSWORD));
            }
        });
    }

    void showRecovery(String playerName, PlayerConfigurationConnection connection) {
        DialogWindowSpec recoverySpec = dialogWindowService.createPreJoinRecoveryDialog(playerName);
        connection.getAudience().showDialog(PaperDialogHelper.createPreJoinRecoveryDialog(recoverySpec));
    }

    void submitRecovery(long sessionId, String playerName, DialogResponseView dialogResponseView) {
        String email = dialogResponseView == null ? null : dialogResponseView.getText("email");
        if (email == null || email.isBlank()) {
            String kickMessage = commonService.getProperty(RegistrationSettings.PRE_JOIN_LOGIN_CANCEL_KICKS)
                ? messages.retrieveSingle(playerName, MessageKey.DIALOG_LOGIN_CANCELED)
                : null;
            PaperDialogSessionSupport.completeResponse(pendingResponses, sessionId, kickMessage);
            return;
        }
        preJoinDialogService.storePendingRecoveryEmail(sessionId, email);
        // Let the player join; AsynchronousJoin will execute the recovery and kick on failure
        PaperDialogSessionSupport.completeResponse(pendingResponses, sessionId, null);
    }
}
