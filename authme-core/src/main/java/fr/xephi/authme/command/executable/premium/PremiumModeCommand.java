package fr.xephi.authme.command.executable.premium;

import fr.xephi.authme.command.PlayerCommand;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.service.PremiumService;
import org.bukkit.entity.Player;

import javax.inject.Inject;
import java.util.List;

/**
 * Common base for the player-facing commands that change premium mode.
 */
public abstract class PremiumModeCommand extends PlayerCommand {

    @Inject
    private PremiumService premiumService;

    @Override
    protected final void runCommand(Player player, List<String> arguments) {
        updatePremiumMode(player);
    }

    /**
     * Applies the premium mode change to the given player.
     *
     * @param player the player whose premium mode should be changed
     */
    protected abstract void updatePremiumMode(Player player);

    @Override
    public MessageKey getArgumentsMismatchMessage() {
        return MessageKey.UNKNOWN_COMMAND;
    }

    protected PremiumService getPremiumService() {
        return premiumService;
    }
}
