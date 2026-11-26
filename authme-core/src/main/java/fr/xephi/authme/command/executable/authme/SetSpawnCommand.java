package fr.xephi.authme.command.executable.authme;

import fr.xephi.authme.message.MessageKey;
import org.bukkit.Location;

/**
 * Sets the spawn to the player's current location.
 */
public class SetSpawnCommand extends SpawnSetterCommand {

    @Override
    protected boolean saveSpawn(Location location) {
        return getSpawnLoader().setSpawn(location);
    }

    @Override
    protected MessageKey getSuccessMessage() {
        return MessageKey.SPAWN_SET_SUCCESS;
    }

    @Override
    protected MessageKey getFailureMessage() {
        return MessageKey.SPAWN_SET_FAIL;
    }
}
