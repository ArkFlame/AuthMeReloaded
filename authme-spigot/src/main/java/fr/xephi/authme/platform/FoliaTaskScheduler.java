package fr.xephi.authme.platform;

import fr.xephi.authme.AuthMe;
import fr.xephi.authme.service.CancellableTask;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

/**
 * Region-safe dispatch for the work the shared {@link SchedulerProvider} does not cover:
 * region ownership checks and tasks bound to an entity's owning thread.
 *
 * <p>Global and asynchronous scheduling is handled by the core FoliaSchedulerProvider
 * through the platform adapter, so it is deliberately not duplicated here.</p>
 */
final class FoliaTaskScheduler {

    private static final CancellableTask NO_OP_TASK = () -> { };

    private static final String RETIRED_SUFFIX = " because the entity scheduler was retired";

    private FoliaTaskScheduler() {
    }

    static boolean isOwnedByCurrentThread(Entity entity) {
        return Bukkit.isOwnedByCurrentRegion(entity);
    }

    static boolean isOwnedByCurrentThread(Location location) {
        return Bukkit.isOwnedByCurrentRegion(location);
    }

    static void runOnEntityThread(AuthMe plugin, Entity entity, Runnable task) {
        cancellableTask(entity.getScheduler().run(plugin, ignored -> task.run(), null), plugin, "entity task");
    }

    static CancellableTask runDelayedOnEntityThread(AuthMe plugin, Entity entity, Runnable task, long delay) {
        return cancellableTask(
            entity.getScheduler().runDelayed(plugin, ignored -> task.run(), null, atLeastOneTick(delay)),
            plugin, "delayed entity task");
    }

    static CancellableTask runAtFixedRateOnEntityThread(AuthMe plugin, Entity entity, Runnable task,
                                                        long delay, long period) {
        return cancellableTask(
            entity.getScheduler().runAtFixedRate(plugin, ignored -> task.run(), null,
                atLeastOneTick(delay), atLeastOneTick(period)),
            plugin, "repeating entity task");
    }

    /** Folia rejects delays and periods below one tick, whereas the Bukkit scheduler treats 0 as "next tick". */
    private static long atLeastOneTick(long ticks) {
        return Math.max(1L, ticks);
    }

    private static CancellableTask cancellableTask(ScheduledTask scheduledTask, AuthMe plugin, String taskKind) {
        if (scheduledTask == null) {
            plugin.getLogger().fine("Skipped " + taskKind + RETIRED_SUFFIX);
            return NO_OP_TASK;
        }
        return scheduledTask::cancel;
    }
}
