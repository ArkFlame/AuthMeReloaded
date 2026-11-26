package fr.xephi.authme.command.executable.authme;

import fr.xephi.authme.message.MessageKey;
import org.bukkit.Location;

/**
 * Sets the first spawn to the player's current location.
 */
public class SetFirstSpawnCommand extends SpawnSetterCommand {

    @Override
    protected boolean saveSpawn(Location location) {
        return getSpawnLoader().setFirstSpawn(location);
    }

    @Override
    protected MessageKey getSuccessMessage() {
        return MessageKey.FIRST_SPAWN_SET_SUCCESS;
    }

    @Override
    protected MessageKey getFailureMessage() {
        return MessageKey.FIRST_SPAWN_SET_FAIL;
    }
}
