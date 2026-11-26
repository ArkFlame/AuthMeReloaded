package fr.xephi.authme.command.executable.premium;

import org.bukkit.entity.Player;

/**
 * Command to disable premium mode for a player.
 */
public class FreemiumCommand extends PremiumModeCommand {

    @Override
    protected void updatePremiumMode(Player player) {
        getPremiumService().disablePremium(player);
    }
}
