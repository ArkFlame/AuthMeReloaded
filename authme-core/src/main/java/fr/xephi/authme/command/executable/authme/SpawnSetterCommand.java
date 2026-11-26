package fr.xephi.authme.command.executable.authme;

import fr.xephi.authme.message.MessageKey;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Common base for commands that store the player's current location as a spawn.
 */
public abstract class SpawnSetterCommand extends AbstractSpawnCommand {

    @Override
    protected final void runCommand(Player player, List<String> arguments) {
        if (saveSpawn(player.getLocation())) {
            messages.send(player, getSuccessMessage());
        } else {
            messages.send(player, getFailureMessage());
        }
    }

    /**
     * Stores the given location as the configured spawn.
     *
     * @param location the location to store
     * @return true if the location was stored successfully
     */
    protected abstract boolean saveSpawn(Location location);

    /**
     * @return the message to show when the spawn was stored successfully
     */
    protected abstract MessageKey getSuccessMessage();

    /**
     * @return the message to show when the spawn could not be stored
     */
    protected abstract MessageKey getFailureMessage();
}
