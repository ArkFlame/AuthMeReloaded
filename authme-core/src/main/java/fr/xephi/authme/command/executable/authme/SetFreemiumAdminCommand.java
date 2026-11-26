package fr.xephi.authme.command.executable.authme;

import org.bukkit.command.CommandSender;

/**
 * Admin command to disable premium mode for a player by name.
 */
public class SetFreemiumAdminCommand extends SetPremiumStateAdminCommand {

    @Override
    protected void updatePremiumState(CommandSender sender, String playerName) {
        getPremiumService().disablePremiumAdmin(sender, playerName);
    }
}
