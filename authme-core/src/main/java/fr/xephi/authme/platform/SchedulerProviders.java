package fr.xephi.authme.platform;

import fr.xephi.authme.util.Utils;

/**
 * Entry point for obtaining a {@link SchedulerProvider}. The provider matching the
 * running server is detected at runtime: Folia servers get the region-aware
 * implementation, all other servers get the plain Bukkit implementation.
 */
public final class SchedulerProviders {

    /** Folia class whose presence identifies a region-threaded server. */
    public static final String FOLIA_SCHEDULER_CLASS =
        "io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler";

    private static final SchedulerProvider CURRENT = isFolia() ? folia() : bukkit();

    private SchedulerProviders() {
    }

    /** @return the provider for the server this plugin is running on */
    public static SchedulerProvider current() {
        return CURRENT;
    }

    /** @return true if the Folia scheduler API is available on this server */
    public static boolean isFolia() {
        return Utils.isClassLoaded(FOLIA_SCHEDULER_CLASS);
    }

    /** @return a provider backed by the Bukkit scheduler */
    public static SchedulerProvider bukkit() {
        return new BukkitSchedulerProvider();
    }

    /** @return a provider backed by Folia's region and async schedulers */
    public static SchedulerProvider folia() {
        return new FoliaSchedulerProvider();
    }
}
