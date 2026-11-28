package fr.xephi.authme;

import com.google.common.base.Throwables;
import fr.xephi.authme.output.LogLevel;
import fr.xephi.authme.settings.Settings;
import fr.xephi.authme.settings.properties.PluginSettings;
import fr.xephi.authme.settings.properties.SecuritySettings;
import fr.xephi.authme.util.ExceptionUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * AuthMe logger.
 */
public final class ConsoleLogger {

    private static final String NEW_LINE = System.lineSeparator();
    /** Formatter which formats dates to something like "[08-16 21:18:46]" for any given LocalDateTime. */
    private static final DateTimeFormatter DATE_FORMAT =
        DateTimeFormatter.ofPattern("'['MM-dd HH:mm:ss']'");

    // Outside references
    private static volatile File logFile;
    private static volatile Logger logger;

    // Shared state
    private static volatile OutputStreamWriter fileWriter;

    // Individual state
    private final String name;
    private volatile LogLevel logLevel = LogLevel.INFO;

    /**
     * Constructor.
     *
     * @param name the name of this logger (the fully qualified class name using it)
     */
    public ConsoleLogger(String name) {
        this.name = name;
    }

    // Configurations

    public static void initialize(Logger logger, File logFile) {
        ConsoleLogger.logger = logger;
        ConsoleLogger.logFile = logFile;
    }

    /**
     * Sets logging settings which are shared by all logger instances.
     *
     * @param settings the settings to read from
     */
    public static void initializeSharedSettings(Settings settings) {
        if (settings.getProperty(SecuritySettings.USE_LOGGING)) {
            initializeFileWriter();
        } else {
            closeFileWriter();
        }
    }

    /**
     * Sets logging settings which are individual to all loggers.
     *
     * @param settings the settings to read from
     */
    public void initializeSettings(Settings settings) {
        this.logLevel = settings.getProperty(PluginSettings.LOG_LEVEL);
    }

    public LogLevel getLogLevel() {
        return logLevel;
    }

    public String getName() {
        return name;
    }

    // Logging methods

    /** Log a WARN message. */
    public void warning(String message) {
        logger.warning(message);
        writeLog("[WARN] " + message);
    }

    /**
     * Log a Throwable with the provided message on WARNING level and save the stack trace to the log file.
     *
     * @param message The message to accompany the exception
     * @param th      The Throwable to log
     */
    public void logException(String message, Throwable th) {
        warning(message + " " + ExceptionUtils.formatException(th));
        writeLog(Throwables.getStackTraceAsString(th));
    }

    /** Log an INFO message. */
    public void info(String message) {
        logger.info(message);
        writeLog("[INFO] " + message);
    }

    /**
     * Log a FINE message if enabled. Logs on INFO level because levels below INFO are disabled by Bukkit/Spigot.
     *
     * @param message The message to log
     */
    public void fine(String message) {
        if (logLevel.includes(LogLevel.FINE)) {
            logger.info(message);
            writeLog("[FINE] " + message);
        }
    }

    // Debug log methods

    /**
     * Log a DEBUG message if enabled. Logs on INFO level with a "DEBUG" prefix because lower levels are disabled.
     *
     * @param message The message to log
     */
    public void debug(String message) {
        if (logLevel.includes(LogLevel.DEBUG)) {
            logAndWriteWithDebugPrefix(message);
        }
    }

    /**
     * Log the DEBUG message.
     *
     * @param message the message
     * @param param1 parameter to replace in the message
     */
    public void debug(String message, Object param1) {
        if (logLevel.includes(LogLevel.DEBUG)) {
            logAndWriteWithDebugPrefix(MessageFormat.format(message, param1));
        }
    }

    /**
     * Log the DEBUG message.
     *
     * @param message the message
     * @param param1 first param to replace in message
     * @param param2 second param to replace in message
     */
    public void debug(String message, Object param1, Object param2) {
        if (logLevel.includes(LogLevel.DEBUG)) {
            logAndWriteWithDebugPrefix(MessageFormat.format(message, param1, param2));
        }
    }

    /**
     * Log the DEBUG message.
     *
     * @param message the message
     * @param params the params to replace in the message
     */
    public void debug(String message, Object... params) {
        if (logLevel.includes(LogLevel.DEBUG)) {
            logAndWriteWithDebugPrefix(MessageFormat.format(message, params));
        }
    }

    /**
     * Log the DEBUG message from the supplier if enabled.
     *
     * @param msgSupplier the message supplier
     */
    public void debug(Supplier<String> msgSupplier) {
        if (logLevel.includes(LogLevel.DEBUG)) {
            logAndWriteWithDebugPrefix(msgSupplier.get());
        }
    }

    private void logAndWriteWithDebugPrefix(String message) {
        String debugMessage = "[DEBUG] " + message;
        logger.info(debugMessage);
        writeLog(debugMessage);
    }

    // Helpers

    /** Closes the file writer. */
    public static synchronized void closeFileWriter() {
        OutputStreamWriter writer = fileWriter;
        if (writer == null) {
            return;
        }
        fileWriter = null;
        try {
            writer.flush();
        } catch (IOException ignored) {
            // Closing below is still the best effort we can make
        } finally {
            try {
                writer.close();
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Failed to close resource", e);
            }
        }
    }

    /**
     * Write a message into the log file with a timestamp when file logging is enabled.
     *
     * @param message The message to write to the log
     */
    private static void writeLog(String message) {
        if (fileWriter == null) {
            return;
        }
        String line = DATE_FORMAT.format(LocalDateTime.now()) + ": " + message + NEW_LINE;
        synchronized (ConsoleLogger.class) {
            if (fileWriter != null) {
                try {
                    fileWriter.write(line);
                    fileWriter.flush();
                } catch (IOException ignored) {
                    // Nothing we can do if writing to the log file fails
                }
            }
        }
    }

    /**
     * Populates the {@link #fileWriter} field if it is null, handling any exceptions that might arise.
     */
    private static synchronized void initializeFileWriter() {
        if (fileWriter == null) {
            try {
                fileWriter = new OutputStreamWriter(new FileOutputStream(logFile, true), StandardCharsets.UTF_8);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to create writer to AuthMe log file", e);
            }
        }
    }
}
