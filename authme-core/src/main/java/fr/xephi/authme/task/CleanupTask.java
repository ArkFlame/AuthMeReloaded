package fr.xephi.authme.task;

import ch.jalu.injector.factory.SingletonStore;
import fr.xephi.authme.ConsoleLogger;
import fr.xephi.authme.initialization.HasCleanup;
import fr.xephi.authme.output.ConsoleLoggerFactory;

import javax.inject.Inject;

/**
 * Task run periodically to invoke the cleanup task on services.
 */
public class CleanupTask implements Runnable {

    private final ConsoleLogger logger = ConsoleLoggerFactory.get(CleanupTask.class);

    @Inject
    private SingletonStore<HasCleanup> hasCleanupStore;

    @Override
    public void run() {
        // An exception escaping a repeating task makes Bukkit cancel it: isolate each service
        hasCleanupStore.retrieveAllOfType().forEach(service -> {
            try {
                service.performCleanup();
            } catch (RuntimeException e) {
                logger.logException("Cleanup of " + service.getClass().getSimpleName() + " failed:", e);
            }
        });
    }
}
