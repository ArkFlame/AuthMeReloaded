package fr.xephi.authme.listener;

import org.bukkit.Location;

/**
 * Folia listener that resolves custom join spawn locations. Folia fires the spawn-location event on the
 * player's owning region thread, so the location can be resolved directly without a scheduler hop; this
 * keeps the operation region-safe.
 */
public class FoliaPlayerSpawnLocationListener extends AbstractPaperAsyncPlayerSpawnLocationListener {

    @Override
    protected Location determineCustomSpawnLocation(String playerName, Location originalSpawnLocation) {
        return teleportationService.prepareOnJoinSpawnLocation(playerName, originalSpawnLocation);
    }
}
