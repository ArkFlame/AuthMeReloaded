package fr.xephi.authme.process.register;

import fr.xephi.authme.ConsoleLogger;
import fr.xephi.authme.output.LogLevel;
import fr.xephi.authme.util.PlayerUtils;
import org.bukkit.entity.Player;

/**
 * Shared FINE-level logging for successful registrations.
 */
final class RegisterLogHelper {

    private RegisterLogHelper() {
    }

    /**
     * Logs that a player has registered, unless FINE logging is disabled. The message and the
     * player's IP are only resolved when the log entry will actually be written.
     *
     * @param logger the logger of the calling registration process
     * @param player the newly registered player
     */
    static void logRegistration(ConsoleLogger logger, Player player) {
        if (logger.getLogLevel().includes(LogLevel.FINE)) {
            logger.fine(player.getName() + " registered " + PlayerUtils.getPlayerIp(player));
        }
    }
}
