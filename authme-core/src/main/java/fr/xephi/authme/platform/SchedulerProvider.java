package fr.xephi.authme.platform;

import fr.xephi.authme.service.CancellableTask;
import org.bukkit.plugin.Plugin;

/**
 * Abstraction over the server's task schedulers, selected at runtime through
 * {@link SchedulerProviders#current()}. It exposes the two execution contexts shared
 * by every supported server: the global/main thread and off-thread workers.
 */
public interface SchedulerProvider {

    /** @return true if the current thread may perform global server work */
    boolean isGlobalThread();

    /** Runs a task on the server's global/main execution context. */
    void runOnGlobalThread(Plugin plugin, Runnable task);

    /** Runs a delayed task on the server's global/main execution context. */
    CancellableTask runDelayedOnGlobalThread(Plugin plugin, Runnable task, long delayTicks);

    /** Runs a repeating task on the server's global/main execution context. */
    CancellableTask runAtFixedRateOnGlobalThread(Plugin plugin, Runnable task, long delayTicks, long periodTicks);

    /** Runs a task asynchronously, off the global/main thread. */
    CancellableTask runAsync(Plugin plugin, Runnable task);

    /** Runs a repeating asynchronous task. */
    CancellableTask runAsyncTimer(Plugin plugin, Runnable task, long delayTicks, long periodTicks);
}
