package fr.xephi.authme.command.executable.authme;

import fr.xephi.authme.ConsoleLogger;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.output.ConsoleLoggerFactory;
import fr.xephi.authme.service.BukkitService;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import javax.inject.Inject;

/**
 * Command to disable two-factor authentication for a user.
 */
public class TotpDisableAdminCommand extends AbstractTotpAdminCommand {

    private final ConsoleLogger logger = ConsoleLoggerFactory.get(TotpDisableAdminCommand.class);
    @Inject
    private BukkitService bukkitService;

    @Override
    protected void handlePlayer(CommandSender sender, String playerName, PlayerAuth auth) {
        if (auth.getTotpKey() == null) {
            sender.sendMessage(ChatColor.RED + "Player '" + playerName + "' does not have two-factor auth enabled");
        } else if (getDataSource().removeTotpKey(playerName)) {
            sender.sendMessage("Disabled two-factor authentication successfully for '" + playerName + "'");
            logger.info(sender.getName() + " disable two-factor authentication for '" + playerName + "'");
            Player onlinePlayer = bukkitService.getPlayerExact(playerName);
            if (onlinePlayer != null) {
                getMessages().send(onlinePlayer, MessageKey.TWO_FACTOR_REMOVED_SUCCESS);
            }
        } else {
            getMessages().send(sender, MessageKey.ERROR);
        }
    }
}
