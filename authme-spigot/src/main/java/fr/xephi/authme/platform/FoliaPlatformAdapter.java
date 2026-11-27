package fr.xephi.authme.platform;

import fr.xephi.authme.AuthMe;
import fr.xephi.authme.listener.FoliaChatListener;
import fr.xephi.authme.listener.FoliaPlayerSpawnLocationListener;
import fr.xephi.authme.service.CancellableTask;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.event.Listener;

import java.util.List;

/**
 * Platform adapter implementation for Folia 1.21.11+.
 * Reuses the Paper-derived API surface exposed by Folia. Region ownership checks and
 * entity-thread work go through {@link FoliaTaskScheduler}; global and asynchronous work
 * is delegated to the core {@link SchedulerProvider} for Folia.
 */
public class FoliaPlatformAdapter extends AbstractPaperPlatformAdapter {

    private final SchedulerProvider scheduler = SchedulerProviders.folia();

    @Override
    public boolean isOwnedByCurrentThread(Entity entity) {
        return FoliaTaskScheduler.isOwnedByCurrentThread(entity);
    }

    @Override
    public boolean isOwnedByCurrentThread(Location location) {
        return FoliaTaskScheduler.isOwnedByCurrentThread(location);
    }

    @Override
    public boolean isGlobalThread() {
        return scheduler.isGlobalThread();
    }

    @Override
    public void runOnEntityThread(AuthMe plugin, Entity entity, Runnable task) {
        FoliaTaskScheduler.runOnEntityThread(plugin, entity, task);
    }

    @Override
    public CancellableTask runDelayedOnEntityThread(AuthMe plugin, Entity entity, Runnable task, long delay) {
        return FoliaTaskScheduler.runDelayedOnEntityThread(plugin, entity, task, delay);
    }

    @Override
    public CancellableTask runAtFixedRateOnEntityThread(AuthMe plugin, Entity entity, Runnable task,
                                                        long delay, long period) {
        return FoliaTaskScheduler.runAtFixedRateOnEntityThread(plugin, entity, task, delay, period);
    }

    @Override
    public CancellableTask runAsyncTask(AuthMe plugin, Runnable task) {
        return scheduler.runAsync(plugin, task);
    }

    @Override
    public CancellableTask runAsyncTaskTimer(AuthMe plugin, Runnable task, long delay, long period) {
        return scheduler.runAsyncTimer(plugin, task, delay, period);
    }

    @Override
    public void runOnGlobalThread(AuthMe plugin, Runnable task) {
        scheduler.runOnGlobalThread(plugin, task);
    }

    @Override
    public CancellableTask runDelayedOnGlobalThread(AuthMe plugin, Runnable task, long delay) {
        return scheduler.runDelayedOnGlobalThread(plugin, task, delay);
    }

    @Override
    public String getPlatformName() {
        return "folia-1.21";
    }

    @Override
    public String getCompatibilityError() {
        return getPaperCompatibilityError("This AuthMe Folia build requires the Folia 1.21.11+ API.",
            "io.papermc.paper.threadedregions.RegionizedServer",
            "io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
    }

    @Override
    protected List<Class<? extends Listener>> getPlatformListeners() {
        return getPaperListeners(FoliaChatListener.class, FoliaPlayerSpawnLocationListener.class);
    }
}
