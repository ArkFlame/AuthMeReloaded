package fr.xephi.authme.command.executable.authme;

import org.bukkit.command.CommandSender;

/**
 * Admin command to enable premium mode for a player by name.
 */
public class SetPremiumAdminCommand extends SetPremiumStateAdminCommand {

    @Override
    protected void updatePremiumState(CommandSender sender, String playerName) {
        getPremiumService().enablePremiumAdmin(sender, playerName);
    }
}
