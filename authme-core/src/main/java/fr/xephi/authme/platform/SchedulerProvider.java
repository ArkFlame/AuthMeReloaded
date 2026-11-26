package fr.xephi.authme.platform;

import fr.xephi.authme.service.CancellableTask;
import org.bukkit.plugin.Plugin;

/**
 * Abstraction over the server's task schedulers.
 *
 * <p>New code should depend on this interface instead of calling Bukkit or Folia
 * scheduler APIs directly. It exposes the two execution contexts shared by every
 * supported server: the global/main thread and off-thread (async) workers.
 * Implementations are selected at runtime through {@link SchedulerProviders#current()}:
 * a plain Bukkit implementation for traditional single-threaded servers and a Folia
 * implementation for region-threaded servers.
 */
public interface SchedulerProvider {

    /**
     * @return true if this provider targets a Folia (region-threaded) server
     */
    boolean isFolia();

    /**
     * @return true if the current thread may perform global server work
     */
    boolean isGlobalThread();

    /**
     * Runs a task on the server's global/main execution context.
     *
     * @param plugin the plugin scheduling the task
     * @param task the task to run
     */
    void runOnGlobalThread(Plugin plugin, Runnable task);

    /**
     * Runs a delayed task on the server's global/main execution context.
     *
     * @param plugin the plugin scheduling the task
     * @param task the task to run
     * @param delayTicks the delay in ticks
     * @return a handle that cancels the scheduled task
     */
    CancellableTask runDelayedOnGlobalThread(Plugin plugin, Runnable task, long delayTicks);

    /**
     * Runs a repeating task on the server's global/main execution context.
     *
     * @param plugin the plugin scheduling the task
     * @param task the task to run
     * @param delayTicks the delay in ticks before the first run
     * @param periodTicks the period in ticks between runs
     * @return a handle that cancels the scheduled task
     */
    CancellableTask runAtFixedRateOnGlobalThread(Plugin plugin, Runnable task, long delayTicks, long periodTicks);

    /**
     * Runs a task asynchronously, off the global/main thread.
     *
     * @param plugin the plugin scheduling the task
     * @param task the task to run
     * @return a handle that cancels the scheduled task
     */
    CancellableTask runAsync(Plugin plugin, Runnable task);

    /**
     * Runs a repeating asynchronous task.
     *
     * @param plugin the plugin scheduling the task
     * @param task the task to run
     * @param delayTicks the initial delay in ticks
     * @param periodTicks the repeat period in ticks
     * @return a handle that cancels the scheduled task
     */
    CancellableTask runAsyncTimer(Plugin plugin, Runnable task, long delayTicks, long periodTicks);
}
