package fr.xephi.authme.command.executable.authme;

import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.platform.TeleportAdapter;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import javax.inject.Inject;
import java.util.List;

/**
 * Common base for commands that teleport the player to a configured spawn.
 */
public abstract class SpawnTeleportCommand extends AbstractSpawnCommand {

    @Inject
    private TeleportAdapter teleportAdapter;

    @Override
    protected final void runCommand(Player player, List<String> arguments) {
        Location spawn = getSpawn();
        if (spawn == null) {
            messages.send(player, getNotDefinedMessage());
            return;
        }
        teleportAdapter.teleportPlayer(player, spawn);
    }

    /** @return the spawn location to teleport to, or null if none is configured */
    protected abstract Location getSpawn();

    /** @return the message to show when no spawn location is configured */
    protected abstract MessageKey getNotDefinedMessage();
}
