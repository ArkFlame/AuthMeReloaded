package fr.xephi.authme.command.executable.authme;

import fr.xephi.authme.command.CommandUtils;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.service.BukkitService;
import org.bukkit.command.CommandSender;

import java.util.Date;
import java.util.List;

import javax.inject.Inject;

/**
 * Returns the last login date of the given user.
 */
public class LastLoginCommand extends AbstractPlayerAuthCommand {

    @Inject
    private BukkitService bukkitService;

    @Override
    public void executeCommand(CommandSender sender, List<String> arguments) {
        String playerName = CommandUtils.getTargetPlayerName(sender, arguments);

        // Database access: keep it off the server thread
        bukkitService.runTaskOptionallyAsync(() -> report(sender, playerName));
    }

    private void report(CommandSender sender, String playerName) {
        PlayerAuth auth = getAuthOrReportUnknown(sender, playerName);
        if (auth == null) {
            return;
        }

        Long lastLogin = auth.getLastLogin();
        if (lastLogin == null) {
            sender.sendMessage("[AuthMe] " + playerName + " last login: never");
        } else {
            long diff = System.currentTimeMillis() - lastLogin;
            sender.sendMessage("[AuthMe] " + playerName + " last login: " + new Date(lastLogin));
            sender.sendMessage("[AuthMe] The player " + playerName + " last logged in " + (int) (diff / 86400000)
                + " days " + (int) (diff / 3600000 % 24) + " hours " + (int) (diff / 60000 % 60) + " mins "
                + (int) (diff / 1000 % 60) + " secs ago");
        }
        sender.sendMessage("[AuthMe] Last player's IP: " + auth.getLastIp());
    }
}
