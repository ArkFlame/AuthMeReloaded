package fr.xephi.authme.command.executable.email;

import fr.xephi.authme.ConsoleLogger;
import fr.xephi.authme.command.PlayerCommand;
import fr.xephi.authme.command.executable.CommandValidation;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.output.ConsoleLoggerFactory;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.security.PasswordSecurity;
import fr.xephi.authme.service.BukkitService;
import fr.xephi.authme.service.CommonService;
import fr.xephi.authme.service.PasswordRecoveryService;
import fr.xephi.authme.service.ValidationService;
import org.bukkit.entity.Player;

import javax.inject.Inject;
import java.util.List;

/**
 * Command for changing password following successful recovery.
 */
public class EmailSetPasswordCommand extends PlayerCommand {

    private final ConsoleLogger logger = ConsoleLoggerFactory.get(EmailSetPasswordCommand.class);

    @Inject
    private DataSource dataSource;

    @Inject
    private CommonService commonService;

    @Inject
    private PasswordRecoveryService recoveryService;

    @Inject
    private PasswordSecurity passwordSecurity;

    @Inject
    private ValidationService validationService;

    @Inject
    private BukkitService bukkitService;

    @Override
    protected void runCommand(Player player, List<String> arguments) {
        if (!recoveryService.canChangePassword(player)) {
            commonService.send(player, MessageKey.CHANGE_PASSWORD_EXPIRED);
            return;
        }

        String name = player.getName();
        String password = arguments.get(0);
        if (!CommandValidation.isValid(commonService, player,
            validationService.validatePassword(password, name))) {
            return;
        }

        // The password hash is deliberately slow: keep it and the database write off the server thread
        bukkitService.runTaskOptionallyAsync(() -> {
            if (!dataSource.updatePassword(name, passwordSecurity.computeHash(password, name))) {
                commonService.send(player, MessageKey.ERROR);
                return;
            }
            recoveryService.removeFromSuccessfulRecovery(player);
            logger.info("Player '" + name + "' has changed their password from recovery");
            commonService.send(player, MessageKey.PASSWORD_CHANGED_SUCCESS);
        });
    }
}
