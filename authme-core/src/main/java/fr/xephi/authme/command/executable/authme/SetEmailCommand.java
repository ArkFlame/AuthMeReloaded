package fr.xephi.authme.command.executable.authme;

import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.data.auth.PlayerCache;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.service.BukkitService;
import fr.xephi.authme.service.ValidationService;
import org.bukkit.command.CommandSender;

import javax.inject.Inject;
import java.util.List;

/**
 * Admin command for setting an email to an account.
 */
public class SetEmailCommand extends AbstractPlayerAuthCommand {

    @Inject
    private PlayerCache playerCache;

    @Inject
    private BukkitService bukkitService;

    @Inject
    private ValidationService validationService;

    @Override
    public void executeCommand(final CommandSender sender, List<String> arguments) {
        // Get the player name and email address
        final String playerName = arguments.get(0);
        final String playerEmail = arguments.get(1);

        // Validate the email address
        if (!validationService.validateEmail(playerEmail)) {
            getCommonService().send(sender, MessageKey.INVALID_EMAIL);
            return;
        }

        bukkitService.runTaskOptionallyAsync(() -> {
            // Validate the user
            PlayerAuth auth = getAuthOrReportUnknown(sender, playerName);
            if (auth == null) {
                return;
            } else if (!validationService.isEmailFreeForRegistration(playerEmail, sender)) {
                getCommonService().send(sender, MessageKey.EMAIL_ALREADY_USED_ERROR);
                return;
            }

            // Set the email address
            auth.setEmail(playerEmail);
            if (!getDataSource().updateEmail(auth)) {
                getCommonService().send(sender, MessageKey.ERROR);
                return;
            }

            // Update the player cache
            playerCache.updatePlayerIfPresent(auth);

            // Show a status message
            getCommonService().send(sender, MessageKey.EMAIL_CHANGED_SUCCESS);
        });
    }
}
