package fr.xephi.authme.command.executable.premium;

import org.bukkit.entity.Player;

/**
 * Command to enable premium mode for a player with an official Minecraft account.
 */
public class PremiumCommand extends PremiumModeCommand {

    @Override
    protected void updatePremiumMode(Player player) {
        getPremiumService().enablePremium(player);
    }
}
