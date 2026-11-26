package fr.xephi.authme.bungee;

import java.nio.file.Path;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Logger;

/**
 * Module-local facade for the shared premium-name helper in {@code fr.xephi.authme.proxy}.
 */
final class PremiumNames extends fr.xephi.authme.proxy.PremiumNames {
    PremiumNames(Path dataDirectory, Logger logger, ScheduledExecutorService executor) {
        super(dataDirectory, new LoggerAdapter() {
            @Override
            public void info(String message) {
                logger.info(message);
            }
            @Override
            public void warning(String message) {
                logger.warning(message);
            }
        }, executor, BungeeAuthenticationStore::normalizeName);
    }
}
