package fr.xephi.authme.command.executable.authme;

import fr.xephi.authme.message.MessageKey;
import org.bukkit.Location;

/**
 * Teleports the player to the first spawn.
 */
public class FirstSpawnCommand extends SpawnTeleportCommand {

    @Override
    protected Location getSpawn() {
        return getSpawnLoader().getFirstSpawn();
    }

    @Override
    protected MessageKey getNotDefinedMessage() {
        return MessageKey.FIRST_SPAWN_NOT_DEFINED;
    }
}
