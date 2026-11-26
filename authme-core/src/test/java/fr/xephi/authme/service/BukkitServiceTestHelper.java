package fr.xephi.authme.service;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.mockito.stubbing.Answer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/**
 * Offers utility methods for testing involving a {@link BukkitService} mock.
 */
public final class BukkitServiceTestHelper {

    private BukkitServiceTestHelper() {
    }

    /**
     * Sets a BukkitService mock to run any Runnable it is passed to its method
     * {@link BukkitService#scheduleSyncTaskFromOptionallyAsyncTask(Runnable)}.
     *
     * @param bukkitService the mock to set behavior on
     */
    public static void setBukkitServiceToScheduleSyncTaskFromOptionallyAsyncTask(BukkitService bukkitService) {
        doAnswer(runRunnableAt(0))
            .when(bukkitService).scheduleSyncTaskFromOptionallyAsyncTask(any(Runnable.class));
    }

    /**
     * Sets a BukkitService mock to run any Runnable it is passed to its method
     * {@link BukkitService#scheduleSyncTaskFromOptionallyAsyncTask(org.bukkit.entity.Entity, Runnable)}.
     *
     * @param bukkitService the mock to set behavior on
     */
    public static void setBukkitServiceToScheduleSyncEntityTaskFromOptionallyAsyncTask(BukkitService bukkitService) {
        doAnswer(runRunnableAt(1))
            .when(bukkitService).scheduleSyncTaskFromOptionallyAsyncTask(any(Entity.class), any(Runnable.class));
    }

    /**
     * Sets a BukkitService mock to run any Runnable it is passed to its method
     * {@link BukkitService#runTaskAsynchronously}.
     *
     * @param bukkitService the mock to set behavior on
     */
    public static void setBukkitServiceToRunTaskAsynchronously(BukkitService bukkitService) {
        doAnswer(runRunnableAt(0)).when(bukkitService).runTaskAsynchronously(any(Runnable.class));
    }

    /**
     * Sets a BukkitService mock to run any Runnable it is passed to its method
     * {@link BukkitService#runTaskOptionallyAsync}.
     *
     * @param bukkitService the mock to set behavior on
     */
    public static void setBukkitServiceToRunTaskOptionallyAsync(BukkitService bukkitService) {
        doAnswer(runRunnableAt(0)).when(bukkitService).runTaskOptionallyAsync(any(Runnable.class));
    }

    /**
     * Sets a BukkitService mock to run any Runnable it is passed to its method
     * {@link BukkitService#scheduleSyncDelayedTask(Runnable)}.
     *
     * @param bukkitService the mock to set behavior on
     */
    public static void setBukkitServiceToScheduleSyncDelayedTask(BukkitService bukkitService) {
        doAnswer(runRunnableAt(0)).when(bukkitService).scheduleSyncDelayedTask(any(Runnable.class));
    }

    /**
     * Sets a BukkitService mock to run any Runnable it is passed to its method
     * {@link BukkitService#scheduleSyncDelayedTask(Runnable, long)}.
     *
     * @param bukkitService the mock to set behavior on
     */
    public static void setBukkitServiceToScheduleSyncDelayedTaskWithDelay(BukkitService bukkitService) {
        doAnswer(runRunnableAt(0)).when(bukkitService).scheduleSyncDelayedTask(any(Runnable.class), anyLong());
    }

    /**
     * Sets a BukkitService mock to run any Runnable it is passed to its method
     * {@link BukkitService#scheduleSyncDelayedTask(Entity, Runnable, long)}.
     *
     * @param bukkitService the mock to set behavior on
     */
    public static void setBukkitServiceToScheduleSyncDelayedTaskWithEntityAndDelay(BukkitService bukkitService) {
        doAnswer(runRunnableAt(1))
            .when(bukkitService).scheduleSyncDelayedTask(any(Entity.class), any(Runnable.class), anyLong());
    }

    public static void setBukkitServiceToRunOnGlobalRegion(BukkitService bukkitService) {
        doAnswer(runRunnableAt(0)).when(bukkitService).runOnGlobalRegion(any(Runnable.class));
    }

    public static void setBukkitServiceToRunTaskLaterOnGlobalRegion(BukkitService bukkitService) {
        doAnswer(runRunnableAtAndReturnTask(0))
            .when(bukkitService).runTaskLaterOnGlobalRegion(any(Runnable.class), anyLong());
    }

    public static void setBukkitServiceToRunTaskLater(BukkitService bukkitService) {
        doAnswer(runRunnableAtAndReturnTask(1))
            .when(bukkitService).runTaskLater(any(Player.class), any(Runnable.class), anyLong());
    }

    private static Answer<Object> runRunnableAt(int argumentIndex) {
        return invocation -> {
            Runnable runnable = invocation.getArgument(argumentIndex);
            runnable.run();
            return null;
        };
    }

    private static Answer<Object> runRunnableAtAndReturnTask(int argumentIndex) {
        return invocation -> {
            Runnable runnable = invocation.getArgument(argumentIndex);
            runnable.run();
            return mock(CancellableTask.class);
        };
    }
}
