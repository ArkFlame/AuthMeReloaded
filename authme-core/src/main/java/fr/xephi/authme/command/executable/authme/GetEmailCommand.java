package fr.xephi.authme.command.executable.authme;

import ch.jalu.datasourcecolumns.data.DataSourceValue;
import fr.xephi.authme.command.CommandUtils;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.service.BukkitService;
import fr.xephi.authme.util.Utils;
import org.bukkit.command.CommandSender;

import javax.inject.Inject;
import java.util.List;

/**
 * Returns a player's email.
 */
public class GetEmailCommand extends AbstractPlayerAuthCommand {

    @Inject
    private BukkitService bukkitService;

    @Override
    public void executeCommand(CommandSender sender, List<String> arguments) {
        String playerName = CommandUtils.getTargetPlayerName(sender, arguments);
        // Database access: keep it off the server thread
        bukkitService.runTaskOptionallyAsync(() -> {
            DataSourceValue<String> email = getDataSource().getEmail(playerName);
            if (!email.rowExists()) {
                getCommonService().send(sender, MessageKey.UNKNOWN_USER);
            } else if (Utils.isEmailEmpty(email.getValue())) {
                getCommonService().send(sender, MessageKey.SHOW_NO_EMAIL);
            } else {
                getCommonService().send(sender, MessageKey.ADMIN_EMAIL_SHOW, playerName, email.getValue());
            }
        });
    }
}
