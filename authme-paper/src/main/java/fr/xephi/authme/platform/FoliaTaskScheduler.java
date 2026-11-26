package fr.xephi.authme.platform;

import fr.xephi.authme.AuthMe;
import fr.xephi.authme.service.CancellableTask;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

import java.util.concurrent.TimeUnit;

/**
 * Region-safe task dispatch for Folia.
 *
 * <p>Folia requires that entity work runs on the owning region's thread, global work on the
 * global region thread, and asynchronous work on the async scheduler. Grouping those calls here
 * keeps the scheduling contract in one place and keeps the platform adapter small.</p>
 */
final class FoliaTaskScheduler {

    private static final CancellableTask NO_OP_TASK = () -> { };

    private static final String ENTITY_TASK_RETIRED =
        "Skipped entity task because the entity scheduler was retired";
    private static final String DELAYED_ENTITY_TASK_RETIRED =
        "Skipped delayed entity task because the entity scheduler was retired";
    private static final String REPEATING_ENTITY_TASK_RETIRED =
        "Skipped repeating entity task because the entity scheduler was retired";

    private FoliaTaskScheduler() {
    }

    static boolean isOwnedByCurrentThread(Entity entity) {
        return Bukkit.isOwnedByCurrentRegion(entity);
    }

    static boolean isOwnedByCurrentThread(Location location) {
        return Bukkit.isOwnedByCurrentRegion(location);
    }

    static boolean isGlobalThread() {
        return Bukkit.isGlobalTickThread();
    }

    static void runOnEntityThread(AuthMe plugin, Entity entity, Runnable task) {
        ScheduledTask scheduledTask = entity.getScheduler().run(plugin, ignored -> task.run(), null);
        if (scheduledTask == null) {
            plugin.getLogger().fine(ENTITY_TASK_RETIRED);
        }
    }

    static CancellableTask runDelayedOnEntityThread(AuthMe plugin, Entity entity, Runnable task, long delay) {
        return cancellableTask(
            entity.getScheduler().runDelayed(plugin, ignored -> task.run(), null, delay),
            plugin, DELAYED_ENTITY_TASK_RETIRED);
    }

    static CancellableTask runAtFixedRateOnEntityThread(AuthMe plugin, Entity entity, Runnable task,
                                                        long delay, long period) {
        return cancellableTask(
            entity.getScheduler().runAtFixedRate(plugin, ignored -> task.run(), null, delay, period),
            plugin, REPEATING_ENTITY_TASK_RETIRED);
    }

    static CancellableTask runAsyncTask(AuthMe plugin, Runnable task) {
        return Bukkit.getAsyncScheduler().runNow(plugin, ignored -> task.run())::cancel;
    }

    static CancellableTask runAsyncTaskTimer(AuthMe plugin, Runnable task, long delay, long period) {
        return Bukkit.getAsyncScheduler()
            .runAtFixedRate(plugin, ignored -> task.run(), ticksToMillis(delay), ticksToMillis(period),
                TimeUnit.MILLISECONDS)::cancel;
    }

    static void runOnGlobalThread(AuthMe plugin, Runnable task) {
        Bukkit.getGlobalRegionScheduler().run(plugin, ignored -> task.run());
    }

    static CancellableTask runDelayedOnGlobalThread(AuthMe plugin, Runnable task, long delay) {
        return Bukkit.getGlobalRegionScheduler().runDelayed(plugin, ignored -> task.run(), delay)::cancel;
    }

    private static CancellableTask cancellableTask(ScheduledTask scheduledTask, AuthMe plugin, String skippedMessage) {
        if (scheduledTask == null) {
            plugin.getLogger().fine(skippedMessage);
            return NO_OP_TASK;
        }
        return scheduledTask::cancel;
    }

    private static long ticksToMillis(long ticks) {
        return ticks * 50L;
    }
}
