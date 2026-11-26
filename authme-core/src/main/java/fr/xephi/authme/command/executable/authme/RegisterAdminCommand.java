package fr.xephi.authme.command.executable.authme;

import fr.xephi.authme.ConsoleLogger;
import fr.xephi.authme.command.executable.CommandValidation;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.output.ConsoleLoggerFactory;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.security.PasswordSecurity;
import fr.xephi.authme.security.crypts.HashedPassword;
import fr.xephi.authme.service.BukkitService;
import fr.xephi.authme.service.ValidationService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import javax.inject.Inject;
import java.util.List;
import java.util.Locale;

/**
 * Admin command to register a user.
 */
public class RegisterAdminCommand extends AbstractPlayerAuthCommand {

    private final ConsoleLogger logger = ConsoleLoggerFactory.get(RegisterAdminCommand.class);

    @Inject
    private PasswordSecurity passwordSecurity;

    @Inject
    private BukkitService bukkitService;

    @Inject
    private ValidationService validationService;

    @Override
    public void executeCommand(final CommandSender sender, List<String> arguments) {
        // Get the player name and password
        final String playerName = arguments.get(0);
        final String playerPass = arguments.get(1);
        final String playerNameLowerCase = playerName.toLowerCase(Locale.ROOT);

        // Command logic
        if (!CommandValidation.isValid(getCommonService(), sender,
            validationService.validatePassword(playerPass, playerName))) {
            return;
        }

        bukkitService.runTaskOptionallyAsync(() -> {
            if (getDataSource().isAuthAvailable(playerNameLowerCase)) {
                getCommonService().send(sender, MessageKey.NAME_ALREADY_REGISTERED);
                return;
            }
            HashedPassword hashedPassword = passwordSecurity.computeHash(playerPass, playerNameLowerCase);
            PlayerAuth auth = PlayerAuth.builder()
                .name(playerNameLowerCase)
                .realName(playerName)
                .password(hashedPassword)
                .registrationDate(System.currentTimeMillis())
                .build();

            if (!getDataSource().saveAuth(auth)) {
                getCommonService().send(sender, MessageKey.ERROR);
                return;
            }

            getCommonService().send(sender, MessageKey.REGISTER_SUCCESS);
            logger.info(sender.getName() + " registered " + playerName);
            final Player player = bukkitService.getPlayerExact(playerName);
            if (player != null) {
                bukkitService.scheduleSyncTaskFromOptionallyAsyncTask(player,
                    () -> player.kickPlayer(getCommonService().retrieveSingleMessage(player,
                        MessageKey.KICK_FOR_ADMIN_REGISTER)));
            }
        });
    }
}
