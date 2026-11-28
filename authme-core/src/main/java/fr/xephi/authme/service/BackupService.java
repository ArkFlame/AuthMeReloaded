package fr.xephi.authme.service;

import fr.xephi.authme.ConsoleLogger;
import fr.xephi.authme.datasource.DataSourceType;
import fr.xephi.authme.initialization.DataFolder;
import fr.xephi.authme.output.ConsoleLoggerFactory;
import fr.xephi.authme.settings.Settings;
import fr.xephi.authme.settings.properties.BackupSettings;
import fr.xephi.authme.settings.properties.DatabaseSettings;
import fr.xephi.authme.util.FileUtils;
import org.bukkit.command.CommandSender;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static fr.xephi.authme.util.Utils.logAndSendMessage;
import static fr.xephi.authme.util.Utils.logAndSendWarning;

/**
 * Performs a backup of the data source.
 */
public class BackupService {

    private final ConsoleLogger logger = ConsoleLoggerFactory.get(BackupService.class);

    private static final long BACKUP_TIMEOUT_MINUTES = 5;

    private final File dataFolder;
    private final File backupFolder;
    private final Settings settings;

    /**
     * Constructor.
     *
     * @param dataFolder the data folder
     * @param settings the plugin settings
     */
    @Inject
    public BackupService(@DataFolder File dataFolder, Settings settings) {
        this.dataFolder = dataFolder;
        this.backupFolder = new File(dataFolder, "backups");
        this.settings = settings;
    }

    /**
     * Performs a backup for the given reason.
     *
     * @param cause backup reason
     */
    public void doBackup(BackupCause cause) {
        doBackup(cause, null);
    }

    /**
     * Performs a backup for the given reason.
     *
     * @param cause backup reason
     * @param sender the command sender (nullable)
     */
    public void doBackup(BackupCause cause, CommandSender sender) {
        if (!settings.getProperty(BackupSettings.ENABLED)) {
            // Print a warning if the backup was requested via command or by another plugin
            if (cause == BackupCause.COMMAND || cause == BackupCause.OTHER) {
                logAndSendWarning(sender,
                    "Can't perform a backup: disabled in configuration. Cause of the backup: " + cause.name());
            }
            return;
        }
        // Don't perform backup on start or stop if so configured
        if (BackupCause.START == cause && !settings.getProperty(BackupSettings.ON_SERVER_START)
                || BackupCause.STOP == cause && !settings.getProperty(BackupSettings.ON_SERVER_STOP)) {
            return;
        }

        if (doBackup()) {
            logAndSendMessage(sender,
                "A backup has been performed successfully. Cause of the backup: " + cause.name());
        } else {
            logAndSendWarning(sender, "Error while performing a backup! Cause of the backup: " + cause.name());
        }
    }

    private boolean doBackup() {
        DataSourceType dataSourceType = settings.getProperty(DatabaseSettings.BACKEND);
        switch (dataSourceType) {
            case MYSQL:
                return performMySqlBackup();
            case SQLITE:
                return performFileBackup(settings.getProperty(DatabaseSettings.MYSQL_DATABASE) + ".db");
            default:
                logger.warning("Unknown data source type '" + dataSourceType + "' for backup");
                return false;
        }
    }

    /**
     * Performs a backup for the MySQL data source.
     *
     * @return true if successful, false otherwise
     */
    private boolean performMySqlBackup() {
        FileUtils.createDirectory(backupFolder);
        File sqlBackupFile = constructBackupFile("sql");

        String backupWindowsPath = settings.getProperty(BackupSettings.MYSQL_WINDOWS_PATH);
        boolean isUsingWindows = useWindowsCommand(backupWindowsPath);
        List<String> backupCommand = buildMysqlDumpCommand(
            isUsingWindows ? backupWindowsPath + "\\bin\\mysqldump.exe" : "mysqldump", sqlBackupFile);

        try {
            ProcessBuilder processBuilder = new ProcessBuilder(backupCommand).redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.INHERIT);
            // The password goes through the environment: it stays out of the process list, and an empty
            // password cannot turn "-p" into an interactive prompt that would hang the backup.
            String password = settings.getProperty(DatabaseSettings.MYSQL_PASSWORD);
            if (!password.isEmpty()) {
                processBuilder.environment().put("MYSQL_PWD", password);
            }
            Process runtimeProcess = processBuilder.start();
            runtimeProcess.getOutputStream().close();
            if (!runtimeProcess.waitFor(BACKUP_TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
                runtimeProcess.destroyForcibly();
                logger.warning("The backup timed out after " + BACKUP_TIMEOUT_MINUTES + " minutes");
                return false;
            }
            if (runtimeProcess.exitValue() == 0) {
                logger.info("Backup created successfully. (Using Windows = " + isUsingWindows + ")");
                return true;
            }
            logger.warning("Could not create the backup! (Using Windows = " + isUsingWindows + ")");
        } catch (IOException | InterruptedException e) {
            logger.logException("Error during backup (using Windows = " + isUsingWindows + "):", e);
        }
        return false;
    }

    private boolean performFileBackup(String filename) {
        FileUtils.createDirectory(backupFolder);
        File backupFile = constructBackupFile("db");

        try {
            Files.copy(new File(dataFolder, filename).toPath(), backupFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException ex) {
            logger.logException("Encountered an error during file backup:", ex);
        }
        return false;
    }

    /**
     * Check if we are under Windows and correct location of mysqldump.exe
     * otherwise return error.
     *
     * @param windowsPath The path to check
     * @return True if the path is correct, false if it is incorrect or the OS is not Windows
     */
    private boolean useWindowsCommand(String windowsPath) {
        if (!System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")) {
            return false;
        }
        if (new File(windowsPath + "\\bin\\mysqldump.exe").exists()) {
            return true;
        }
        logger.warning("Mysql Windows Path is incorrect. Please check it");
        return false;
    }

    /**
     * Builds the {@code mysqldump} command as separate arguments, so paths containing spaces stay intact.
     * The password is not part of the arguments; see {@link #performMySqlBackup()}.
     *
     * @param executable the mysqldump executable
     * @param sqlBackupFile the file to back up to
     * @return the mysqldump command
     */
    private List<String> buildMysqlDumpCommand(String executable, File sqlBackupFile) {
        // Dump the server AuthMe is configured to use, not whatever mysqldump defaults to (local socket).
        return Arrays.asList(executable,
            "--host=" + settings.getProperty(DatabaseSettings.MYSQL_HOST),
            "--port=" + settings.getProperty(DatabaseSettings.MYSQL_PORT),
            "-u", settings.getProperty(DatabaseSettings.MYSQL_USERNAME),
            settings.getProperty(DatabaseSettings.MYSQL_DATABASE),
            "--tables", settings.getProperty(DatabaseSettings.MYSQL_TABLE),
            "-r", sqlBackupFile.getPath() + ".sql");
    }

    /**
     * Constructs the file name to back up the data source to.
     *
     * @param fileExtension the file extension to use (e.g. sql)
     * @return the file to back up the data to
     */
    private File constructBackupFile(String fileExtension) {
        return new File(backupFolder, "backup" + FileUtils.createCurrentTimeString() + "." + fileExtension);
    }

    /**
     * Possible backup causes.
     */
    public enum BackupCause {
        START,
        STOP,
        COMMAND,
        OTHER
    }

}
