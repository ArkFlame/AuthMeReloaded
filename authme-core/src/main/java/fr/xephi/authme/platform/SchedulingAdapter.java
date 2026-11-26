package fr.xephi.authme.platform;

import fr.xephi.authme.AuthMe;
import fr.xephi.authme.service.CancellableTask;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

/**
 * Platform-specific task scheduling bridge used by {@link fr.xephi.authme.service.BukkitService}.
 * Implementations map AuthMe's global and entity-owned task concepts to the appropriate
 * scheduler model of the running server platform.
 */
public interface SchedulingAdapter {

    /** @return true if the current thread may already safely interact with the entity */
    boolean isOwnedByCurrentThread(Entity entity);

    /** @return true if the current thread may already safely interact with the location's world area */
    boolean isOwnedByCurrentThread(Location location);

    /** @return true if the current thread may already safely perform global server work */
    boolean isGlobalThread();

    /** Runs a task on the owning thread of the given entity. */
    void runOnEntityThread(AuthMe plugin, Entity entity, Runnable task);

    /** Runs a delayed task on the owning thread of the given entity. */
    CancellableTask runDelayedOnEntityThread(AuthMe plugin, Entity entity, Runnable task, long delay);

    /** Runs a repeating task on the owning thread of the given entity. */
    CancellableTask runAtFixedRateOnEntityThread(AuthMe plugin, Entity entity, Runnable task, long delay, long period);

    /** Runs a one-off background task off the global and entity-owned threads. */
    CancellableTask runAsyncTask(AuthMe plugin, Runnable task);

    /** Runs a repeating background task off the global and entity-owned threads. */
    CancellableTask runAsyncTaskTimer(AuthMe plugin, Runnable task, long delay, long period);

    /** Runs a task on the platform's global execution context. */
    void runOnGlobalThread(AuthMe plugin, Runnable task);

    /** Runs a delayed task on the platform's global execution context. */
    CancellableTask runDelayedOnGlobalThread(AuthMe plugin, Runnable task, long delay);
}
