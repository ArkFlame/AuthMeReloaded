package fr.xephi.authme.command.executable.authme;

import fr.xephi.authme.data.auth.PlayerAuth;
import org.bukkit.command.CommandSender;

import java.util.Date;
import java.util.List;

/**
 * Returns the last login date of the given user.
 */
public class LastLoginCommand extends AbstractPlayerAuthCommand {

    @Override
    public void executeCommand(CommandSender sender, List<String> arguments) {
        String playerName = CommandArguments.getTargetPlayerName(sender, arguments);

        PlayerAuth auth = getAuthOrReportUnknown(sender, playerName);
        if (auth == null) {
            return;
        }

        // Get the last login date
        final Long lastLogin = auth.getLastLogin();
        final String lastLoginDate = lastLogin == null ? "never" : new Date(lastLogin).toString();

        // Show the player status
        sender.sendMessage("[AuthMe] " + playerName + " last login: " + lastLoginDate);
        if (lastLogin != null) {
            long diff = System.currentTimeMillis() - lastLogin;
            sender.sendMessage("[AuthMe] The player " + playerName + " last logged in "
                + (int) (diff / 86400000) + " days "
                + (int) (diff / 3600000 % 24) + " hours "
                + (int) (diff / 60000 % 60) + " mins "
                + (int) (diff / 1000 % 60) + " secs ago");
        }
        sender.sendMessage("[AuthMe] Last player's IP: " + auth.getLastIp());
    }
}
