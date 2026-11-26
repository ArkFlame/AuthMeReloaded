package fr.xephi.authme.command.executable.email;

import ch.jalu.datasourcecolumns.data.DataSourceValue;
import fr.xephi.authme.ConsoleLogger;
import fr.xephi.authme.data.auth.PlayerCache;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.output.ConsoleLoggerFactory;
import fr.xephi.authme.mail.EmailService;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.service.BukkitService;
import fr.xephi.authme.service.EmailRecoveryDialogService;
import fr.xephi.authme.service.PasswordRecoveryService;
import fr.xephi.authme.service.RecoveryCodeService;
import fr.xephi.authme.util.Utils;
import org.bukkit.entity.Player;

import javax.inject.Inject;
import java.util.List;

/**
 * Command for password recovery by email.
 */
public class RecoverEmailCommand extends AbstractEmailCommand {

    private final ConsoleLogger logger = ConsoleLoggerFactory.get(RecoverEmailCommand.class);

    @Inject
    private DataSource dataSource;

    @Inject
    private PlayerCache playerCache;

    @Inject
    private EmailService emailService;

    @Inject
    private PasswordRecoveryService recoveryService;

    @Inject
    private RecoveryCodeService recoveryCodeService;

    @Inject
    private BukkitService bukkitService;

    @Inject
    private EmailRecoveryDialogService dialogRecoveryService;

    @Override
    protected void runCommand(Player player, List<String> arguments) {
        final String playerMail = arguments.get(0);
        if (dialogRecoveryService.isDialogFlowEnabled()) {
            dialogRecoveryService.requestRecovery(player, playerMail);
            return;
        }

        final String playerName = player.getName();

        if (!emailService.hasAllInformation()) {
            logger.warning("Mail API is not set");
            getCommonService().send(player, MessageKey.INCOMPLETE_EMAIL_SETTINGS);
            return;
        }
        if (playerCache.isAuthenticated(playerName)) {
            getCommonService().send(player, MessageKey.ALREADY_LOGGED_IN_ERROR);
            return;
        }
        if (!isEmailValid(player, playerMail, MessageKey.INVALID_EMAIL)) {
            return;
        }

        DataSourceValue<String> emailResult = dataSource.getEmail(playerName);
        if (!emailResult.rowExists()) {
            getCommonService().send(player, MessageKey.USAGE_REGISTER);
            return;
        }

        final String email = emailResult.getValue();
        if (Utils.isEmailEmpty(email) || !email.equalsIgnoreCase(playerMail)) {
            getCommonService().send(player, MessageKey.INVALID_EMAIL);
            return;
        }

        bukkitService.runTaskAsynchronously(() -> {
            if (recoveryCodeService.isRecoveryCodeNeeded()) {
                recoveryService.createAndSendRecoveryCode(player, email);
            } else {
                recoveryService.generateAndSendNewPassword(player, email);
            }
        });
    }

    @Override
    public MessageKey getArgumentsMismatchMessage() {
        return MessageKey.USAGE_RECOVER_EMAIL;
    }
}
