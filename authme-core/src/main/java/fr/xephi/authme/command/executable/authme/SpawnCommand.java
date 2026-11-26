package fr.xephi.authme.command.executable.authme;

import fr.xephi.authme.message.MessageKey;
import org.bukkit.Location;

/**
 * Teleports the player to the spawn.
 */
public class SpawnCommand extends SpawnTeleportCommand {

    @Override
    protected Location getSpawn() {
        return getSpawnLoader().getSpawn();
    }

    @Override
    protected MessageKey getNotDefinedMessage() {
        return MessageKey.SPAWN_NOT_DEFINED;
    }
}
