package fr.xephi.authme.command.executable.authme;

import fr.xephi.authme.data.auth.PlayerAuth;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

/**
 * Command to see whether a user has enabled two-factor authentication.
 */
public class TotpViewStatusCommand extends AbstractTotpAdminCommand {

    @Override
    protected void handlePlayer(CommandSender sender, String playerName, PlayerAuth auth) {
        if (auth.getTotpKey() == null) {
            sender.sendMessage(ChatColor.RED + "Player '" + playerName + "' does NOT have two-factor auth enabled");
        } else {
            sender.sendMessage(ChatColor.DARK_GREEN + "Player '" + playerName + "' has enabled two-factor authentication");
        }
    }
}
