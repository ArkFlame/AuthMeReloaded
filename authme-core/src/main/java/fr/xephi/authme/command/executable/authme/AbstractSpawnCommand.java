package fr.xephi.authme.command.executable.authme;

import fr.xephi.authme.command.PlayerCommand;
import fr.xephi.authme.settings.SpawnLoader;

import javax.inject.Inject;

/**
 * Common base for the commands that read or write AuthMe's configured spawn locations.
 */
public abstract class AbstractSpawnCommand extends PlayerCommand {

    @Inject
    private SpawnLoader spawnLoader;

    protected final SpawnLoader getSpawnLoader() {
        return spawnLoader;
    }
}
