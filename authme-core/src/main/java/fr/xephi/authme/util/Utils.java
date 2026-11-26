package fr.xephi.authme.util;

import fr.xephi.authme.ConsoleLogger;
import fr.xephi.authme.output.ConsoleLoggerFactory;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;

import java.util.Collection;
import java.util.regex.Pattern;

/**
 * Utility class for various operations used in the codebase.
 */
public final class Utils {

    /** Number of milliseconds in a minute. */
    public static final long MILLIS_PER_MINUTE = 60_000L;

    private static final ConsoleLogger logger = ConsoleLoggerFactory.get(Utils.class);
    private static final Pattern MATCH_ALL_PATTERN = Pattern.compile(".*?");

    // Utility class
    private Utils() {
    }

    /**
     * Compile Pattern sneaky without throwing Exception.
     *
     * @param pattern pattern string to compile
     *
     * @return the given regex compiled into Pattern object.
     */
    public static Pattern safePatternCompile(String pattern) {
        try {
            return Pattern.compile(pattern);
        } catch (Exception e) {
            logger.warning("Failed to compile pattern '" + pattern + "' - defaulting to allowing everything");
            return MATCH_ALL_PATTERN;
        }
    }

    /**
     * Returns whether the class exists in the current class loader.
     *
     * @param className the class name to check
     *
     * @return true if the class is loaded, false otherwise
     */
    public static boolean isClassLoaded(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Sends a message to the given sender (null safe), and logs the message to the console.
     * This method is aware that the command sender might be the console sender and avoids
     * displaying the message twice in this case.
     *
     * @param sender the sender to inform
     * @param message the message to log and send
     */
    public static void logAndSendMessage(CommandSender sender, String message) {
        logger.info(message);
        sendToNonConsole(sender, message);
    }

    /**
     * Sends a warning to the given sender (null safe), and logs the warning to the console.
     * This method is aware that the command sender might be the console sender and avoids
     * displaying the message twice in this case.
     *
     * @param sender the sender to inform
     * @param message the warning to log and send
     */
    public static void logAndSendWarning(CommandSender sender, String message) {
        logger.warning(message);
        sendToNonConsole(sender, ChatColor.RED + message);
    }

    /**
     * Sends the message to the sender unless it is the console, which already
     * sees everything written by {@link #logger}.
     *
     * @param sender the sender to inform, may be null
     * @param message the message to send
     */
    private static void sendToNonConsole(CommandSender sender, String message) {
        if (sender != null && !(sender instanceof ConsoleCommandSender)) {
            sender.sendMessage(message);
        }
    }

    /**
     * Null-safe way to check whether a collection is empty or not.
     *
     * @param coll The collection to verify
     * @return True if the collection is null or empty, false otherwise
     */
    public static boolean isCollectionEmpty(Collection<?> coll) {
        return coll == null || coll.isEmpty();
    }

    /**
     * Returns whether the given email is empty or equal to the standard "undefined" email address.
     *
     * @param email the email to check
     *
     * @return true if the email is empty
     */
    public static boolean isEmailEmpty(String email) {
        return StringUtils.isBlank(email) || "your@email.com".equalsIgnoreCase(email);
    }
}
