package fr.xephi.authme.service;

import ch.jalu.datasourcecolumns.data.DataSourceValue;
import fr.xephi.authme.data.auth.PlayerCache;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.mail.EmailService;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.platform.DialogAdapter;
import fr.xephi.authme.platform.EmailRecoveryDialogSpec;
import fr.xephi.authme.settings.properties.RegistrationSettings;
import fr.xephi.authme.util.Utils;
import org.bukkit.entity.Player;

import javax.inject.Inject;

/**
 * Optional in-dialog e-mail recovery flow. It delegates every check to the services used by the
 * chat commands, so cooldowns, code expiry, messages and privacy guarantees are unchanged when
 * dialogs are disabled or unsupported.
 */
public class EmailRecoveryDialogService {

    @Inject
    private CommonService commonService;

    @Inject
    private DialogAdapter dialogAdapter;

    @Inject
    private EmailService emailService;

    @Inject
    private DataSource dataSource;

    @Inject
    private PlayerCache playerCache;

    @Inject
    private ValidationService validationService;

    @Inject
    private RecoveryCodeService recoveryCodeService;

    @Inject
    private PasswordRecoveryService passwordRecoveryService;

    @Inject
    private BukkitService bukkitService;

    /** Whether the opt-in follow-up dialogs may be used on this server. */
    public boolean isDialogFlowEnabled() {
        return commonService.getProperty(RegistrationSettings.DIALOG_EMAIL_RECOVERY)
            && dialogAdapter.isDialogSupported()
            && emailService.hasAllInformation();
    }

    /**
     * Requests a recovery code (or a new password when codes are disabled) and offers the code
     * dialog once the mail has been handed to the existing recovery service.
     *
     * @param player the recovering player
     * @param email the e-mail address supplied by the player
     */
    public void requestRecovery(Player player, String email) {
        String playerName = player.getName();
        if (!emailService.hasAllInformation()) {
            commonService.send(player, MessageKey.INCOMPLETE_EMAIL_SETTINGS);
            return;
        }
        if (playerCache.isAuthenticated(playerName)) {
            commonService.send(player, MessageKey.ALREADY_LOGGED_IN_ERROR);
            return;
        }
        if (!validationService.validateEmail(email)) {
            commonService.send(player, MessageKey.INVALID_EMAIL);
            return;
        }

        DataSourceValue<String> emailResult = dataSource.getEmail(playerName);
        if (!emailResult.rowExists()) {
            commonService.send(player, MessageKey.USAGE_REGISTER);
            return;
        }
        String registeredEmail = emailResult.getValue();
        if (Utils.isEmailEmpty(registeredEmail) || !registeredEmail.equalsIgnoreCase(email)) {
            commonService.send(player, MessageKey.INVALID_EMAIL);
            return;
        }

        final boolean codeNeeded = recoveryCodeService.isRecoveryCodeNeeded();
        final boolean rateLimited = passwordRecoveryService.isOnEmailCooldown(player);
        bukkitService.runTaskAsynchronously(() -> {
            if (codeNeeded) {
                passwordRecoveryService.createAndSendRecoveryCode(player, registeredEmail);
            } else {
                passwordRecoveryService.generateAndSendNewPassword(player, registeredEmail);
            }
            // A cooldown only produces a message; there is no fresh code to enter.
            if (codeNeeded && !rateLimited && isDialogFlowEnabled()) {
                bukkitService.scheduleSyncTaskFromOptionallyAsyncTask(player, () -> showCodeDialog(player));
            }
        });
    }

    /**
     * Submits a recovery code with the same checks and messages as {@code /email code}.
     *
     * @param player the recovering player
     * @param code the code from the recovery e-mail
     */
    public void submitCode(Player player, String code) {
        String name = player.getName();
        if (recoveryCodeService.hasTriesLeft(name)) {
            if (recoveryCodeService.isCodeValid(name, code)) {
                commonService.send(player, MessageKey.RECOVERY_CODE_CORRECT);
                passwordRecoveryService.addSuccessfulRecovery(player);
                recoveryCodeService.removeCode(name);
                if (isDialogFlowEnabled()) {
                    showNewPasswordDialog(player);
                }
            } else {
                commonService.send(player, MessageKey.INCORRECT_RECOVERY_CODE,
                    Integer.toString(recoveryCodeService.getTriesLeft(name)));
            }
        } else {
            recoveryCodeService.removeCode(name);
            commonService.send(player, MessageKey.RECOVERY_TRIES_EXCEEDED);
        }
    }

    /** Shows the dialog for entering the recovery code. */
    public void showCodeDialog(Player player) {
        dialogAdapter.showRecoveryCodeDialog(player, EmailRecoveryDialogSpec.createCodeDialog(
            key -> commonService.retrieveSingleMessage(player, key),
            commonService.getProperty(RegistrationSettings.DIALOG_SHOW_BODY),
            commonService.getProperty(RegistrationSettings.DIALOG_STACK_BUTTONS_VERTICALLY)));
    }

    /** Shows the dialog for choosing the new password. */
    public void showNewPasswordDialog(Player player) {
        dialogAdapter.showNewPasswordDialog(player, EmailRecoveryDialogSpec.createNewPasswordDialog(
            key -> commonService.retrieveSingleMessage(player, key),
            commonService.getProperty(RegistrationSettings.DIALOG_SHOW_BODY),
            commonService.getProperty(RegistrationSettings.DIALOG_STACK_BUTTONS_VERTICALLY)));
    }
}
