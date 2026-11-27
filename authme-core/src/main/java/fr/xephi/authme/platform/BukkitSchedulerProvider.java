package fr.xephi.authme.platform;

import fr.xephi.authme.service.CancellableTask;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * {@link SchedulerProvider} for traditional Bukkit/Spigot servers, which have a single
 * main thread that owns the whole world. Obtain instances through
 * {@link SchedulerProviders#current()} or {@link SchedulerProviders#bukkit()}.
 */
final class BukkitSchedulerProvider implements SchedulerProvider {

    @Override
    public boolean isGlobalThread() {
        return Bukkit.isPrimaryThread();
    }

    @Override
    public void runOnGlobalThread(Plugin plugin, Runnable task) {
        Bukkit.getScheduler().runTask(plugin, task);
    }

    @Override
    public CancellableTask runDelayedOnGlobalThread(Plugin plugin, Runnable task, long delayTicks) {
        return asCancellable(Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks));
    }

    @Override
    public CancellableTask runAtFixedRateOnGlobalThread(Plugin plugin, Runnable task,
                                                        long delayTicks, long periodTicks) {
        return asCancellable(Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks));
    }

    @Override
    public CancellableTask runAsync(Plugin plugin, Runnable task) {
        return asCancellable(Bukkit.getScheduler().runTaskAsynchronously(plugin, task));
    }

    @Override
    public CancellableTask runAsyncTimer(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        return asCancellable(Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, delayTicks, periodTicks));
    }

    private static CancellableTask asCancellable(BukkitTask task) {
        return task::cancel;
    }
}
