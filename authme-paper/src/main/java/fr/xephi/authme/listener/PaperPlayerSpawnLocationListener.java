package fr.xephi.authme.listener;

import fr.xephi.authme.service.BukkitService;
import org.bukkit.Location;

import javax.inject.Inject;

/**
 * Paper listener that resolves custom join spawn locations for async spawn events. Paper fires the event
 * off the main thread, so the location is resolved through {@link BukkitService} on the main thread.
 */
public class PaperPlayerSpawnLocationListener extends AbstractPaperAsyncPlayerSpawnLocationListener {

    @Inject
    private BukkitService bukkitService;

    @Override
    protected Location determineCustomSpawnLocation(String playerName, Location originalSpawnLocation) {
        return bukkitService.callSyncMethodFromOptionallyAsyncTask(
            () -> getTeleportationService().prepareOnJoinSpawnLocation(playerName, originalSpawnLocation));
    }
}
