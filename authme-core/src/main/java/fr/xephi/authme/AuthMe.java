package fr.xephi.authme;

import ch.jalu.injector.Injector;
import ch.jalu.injector.InjectorBuilder;
import com.google.common.annotations.VisibleForTesting;
import fr.xephi.authme.api.v3.AuthMeApi;
import fr.xephi.authme.command.CommandHandler;
import fr.xephi.authme.command.CommandInitializer;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.initialization.DataFolder;
import fr.xephi.authme.initialization.DataSourceProvider;
import fr.xephi.authme.initialization.OnShutdownPlayerSaver;
import fr.xephi.authme.initialization.OnStartupTasks;
import fr.xephi.authme.initialization.SettingsProvider;
import fr.xephi.authme.initialization.TaskCloser;
import fr.xephi.authme.output.ConsoleLoggerFactory;
import fr.xephi.authme.platform.SchedulingAdapter;
import fr.xephi.authme.security.crypts.Sha256;
import fr.xephi.authme.service.BackupService;
import fr.xephi.authme.service.BukkitService;
import fr.xephi.authme.service.MigrationService;
import fr.xephi.authme.service.bungeecord.BungeeReceiver;
import fr.xephi.authme.service.yaml.YamlParseException;
import fr.xephi.authme.settings.Settings;
import fr.xephi.authme.settings.SettingsWarner;
import fr.xephi.authme.settings.properties.SecuritySettings;
import fr.xephi.authme.task.CleanupTask;
import fr.xephi.authme.task.purge.PurgeService;
import fr.xephi.authme.util.ExceptionUtils;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.java.JavaPluginLoader;
import org.bukkit.scheduler.BukkitScheduler;

import fr.xephi.authme.platform.BukkitCompatibilityAdapter;
import fr.xephi.authme.platform.ChatAdapter;
import fr.xephi.authme.platform.CommandRegistrationAdapter;
import fr.xephi.authme.platform.DialogAdapter;
import fr.xephi.authme.platform.EventRegistrationAdapter;
import fr.xephi.authme.platform.PacketInterceptionAdapter;
import fr.xephi.authme.platform.PlatformAdapter;
import fr.xephi.authme.platform.TeleportAdapter;

import java.io.File;
import java.util.Iterator;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.function.Consumer;

import static fr.xephi.authme.service.BukkitService.TICKS_PER_MINUTE;

/**
 * The AuthMe main class.
 */
public class AuthMe extends JavaPlugin {

    // Constants
    private static final String PLUGIN_NAME = "AuthMeRemastered";
    private static final String PLUGIN_AUTHOR = "ArkFlame Studios";
    private static final String LOG_FILENAME = "authme.log";
    private static final int CLEANUP_INTERVAL = 5 * TICKS_PER_MINUTE;
    private static final int MAX_SKIPPED_ADAPTERS = 16;

    // Version and build number values
    private static String pluginVersion = "N/D";
    private static String pluginBuildNumber = "Unknown";

    // Private instances
    private CommandHandler commandHandler;
    private Settings settings;
    private DataSource database;
    private BukkitService bukkitService;
    private Injector injector;
    private BackupService backupService;
    private ConsoleLogger logger;

    /**
     * Constructor.
     */
    public AuthMe() {
    }

    /*
     * Constructor for unit testing.
     */
    @VisibleForTesting
    AuthMe(JavaPluginLoader loader, PluginDescriptionFile description, File dataFolder, File file) {
        super(loader, description, dataFolder, file);
    }

    /**
     * Get the plugin's name.
     *
     * @return The plugin's name.
     */
    public static String getPluginName() {
        return PLUGIN_NAME;
    }

    /**
     * Gets the display author of this distribution.
     *
     * @return the author shown in messages and logs
     */
    public static String getPluginAuthor() {
        return PLUGIN_AUTHOR;
    }

    /**
     * Get the plugin's version.
     *
     * @return The plugin's version.
     */
    public static String getPluginVersion() {
        return pluginVersion;
    }

    /**
     * Get the plugin's build number.
     *
     * @return The plugin's build number.
     */
    public static String getPluginBuildNumber() {
        return pluginBuildNumber;
    }

    /**
     * Method called when the server enables the plugin.
     */
    @Override
    public void onEnable() {
        // Load the plugin version data from the plugin description file
        loadPluginInfo(getDescription().getVersion());

        // Set the Logger instance and log file path
        ConsoleLogger.initialize(getLogger(), new File(getDataFolder(), LOG_FILENAME));
        logger = ConsoleLoggerFactory.get(AuthMe.class);

        PlatformSelection platformSelection = loadPlatformAdapter();
        if (!isStartupAllowed(platformSelection)) {
            return;
        }
        if (!initializeWithErrorHandling(platformSelection.adapter)) {
            return;
        }

        // Show settings warnings
        injector.getSingleton(SettingsWarner.class).logWarningsForMisconfigurations();

        // Schedule clean up task
        CleanupTask cleanupTask = injector.getSingleton(CleanupTask.class);
        bukkitService.runTaskTimerAsynchronously(cleanupTask, CLEANUP_INTERVAL, CLEANUP_INTERVAL);

        // Do a backup on start
        backupService.doBackup(BackupService.BackupCause.START);

        // Set up Metrics
        OnStartupTasks.sendMetrics(this, settings);

        // Successful message
        logger.info(getPluginName() + " by " + getPluginAuthor() + " " + getPluginVersion() + " build n." + getPluginBuildNumber() + " successfully enabled!");

        // Purge on start if enabled
        injector.getSingleton(PurgeService.class).runAutoPurge();
    }

    /**
     * Checks whether the running environment allows the plugin to start, stopping it otherwise.
     *
     * @param platformSelection the platform adapter selected at startup
     * @return true if startup may continue, false if the plugin was stopped
     */
    private boolean isStartupAllowed(PlatformSelection platformSelection) {
        String compatibilityError = platformSelection.compatibilityError;
        if (compatibilityError != null) {
            logger.warning("You are running an unsupported server version (" + getServerNameVersionSafe() + "). "
                + compatibilityError);
            stopOrUnload();
            return false;
        }

        // Prevent running AuthMeBridge due to major exploit issues
        if (getServer().getPluginManager().isPluginEnabled("AuthMeBridge")) {
            logger.warning("Detected AuthMeBridge, support for it has been dropped as it was "
                + "causing exploit issues, please use AuthMeBungee instead! Aborting!");
            stopOrUnload();
            return false;
        }
        return true;
    }

    /**
     * Initializes the plugin, aborting with a diagnostic if initialization fails.
     *
     * @param platformAdapter the platform adapter in use
     * @return true if initialization succeeded, false if the plugin was stopped
     */
    private boolean initializeWithErrorHandling(PlatformAdapter platformAdapter) {
        try {
            initialize(platformAdapter);
        } catch (Throwable th) {
            YamlParseException yamlParseException = ExceptionUtils.findThrowableInCause(YamlParseException.class, th);
            if (yamlParseException == null) {
                logger.logException("Aborting initialization of AuthMe:", th);
                th.printStackTrace();
            } else {
                logger.logException("File '" + yamlParseException.getFile() + "' contains invalid YAML. "
                    + "Please run its contents through http://yamllint.com", yamlParseException);
            }
            stopOrUnload();
            return false;
        }
        return true;
    }

    /**
     * Load the version and build number of the plugin from the description file.
     *
     * @param versionRaw the version as given by the plugin description file
     */
    private static void loadPluginInfo(String versionRaw) {
        int index = versionRaw.lastIndexOf("-");
        if (index != -1) {
            pluginVersion = versionRaw.substring(0, index);
            pluginBuildNumber = versionRaw.substring(index + 1);
            if (pluginBuildNumber.startsWith("b")) {
                pluginBuildNumber = pluginBuildNumber.substring(1);
            }
        }
    }

    /**
     * Initialize the plugin and all the services.
     */
    private void initialize(PlatformAdapter platformAdapter) {
        // Create plugin folder
        getDataFolder().mkdir();

        // Create injector, provide elements from the Bukkit environment and register providers
        injector = createInjector(platformAdapter);

        // Get settings and set up logger
        settings = injector.getSingleton(Settings.class);
        ConsoleLoggerFactory.reloadSettings(settings);
        OnStartupTasks.setupConsoleFilter(getLogger());

        // Set all service fields on the AuthMe class
        instantiateServices(injector);

        CommandInitializer commandInitializer = injector.getSingleton(CommandInitializer.class);
        platformAdapter.registerCommands(this, commandHandler, commandInitializer.getCommands());

        // Convert deprecated PLAINTEXT hash entries
        MigrationService.changePlainTextToSha256(settings, database, new Sha256());

        // If the server is empty (fresh start) just set all the players as unlogged
        if (bukkitService.getOnlinePlayers().isEmpty()) {
            database.purgeLogged();
        }

        // Register event listeners
        registerEventListeners(injector);

        // Start Email recall task if needed
        OnStartupTasks onStartupTasks = injector.newInstance(OnStartupTasks.class);
        onStartupTasks.scheduleRecallEmailTask();
    }

    /**
     * Creates the injector and registers the Bukkit environment, the providers and the platform adapters.
     *
     * @param platformAdapter the platform adapter in use
     * @return the configured injector
     */
    private Injector createInjector(PlatformAdapter platformAdapter) {
        Injector injector = new InjectorBuilder()
            .addDefaultHandlers("fr.xephi.authme")
            .create();
        Server server = getServer();
        injector.register(AuthMe.class, this);
        injector.register(Server.class, server);
        injector.register(PluginManager.class, server.getPluginManager());
        injector.register(BukkitScheduler.class, server.getScheduler());
        injector.provide(DataFolder.class, getDataFolder());
        injector.registerProvider(Settings.class, SettingsProvider.class);
        injector.registerProvider(DataSource.class, DataSourceProvider.class);
        registerPlatformAdapters(injector, platformAdapter);
        return injector;
    }

    /**
     * Registers the platform adapter under all the adapter interfaces it implements.
     *
     * @param injector the injector
     * @param platformAdapter the platform adapter in use
     */
    private void registerPlatformAdapters(Injector injector, PlatformAdapter platformAdapter) {
        injector.register(PlatformAdapter.class, platformAdapter);
        injector.register(BukkitCompatibilityAdapter.class, platformAdapter);
        injector.register(TeleportAdapter.class, platformAdapter);
        injector.register(ChatAdapter.class, platformAdapter);
        injector.register(EventRegistrationAdapter.class, platformAdapter);
        injector.register(SchedulingAdapter.class, platformAdapter);
        injector.register(DialogAdapter.class, platformAdapter);
        injector.register(CommandRegistrationAdapter.class, platformAdapter);
        injector.register(PacketInterceptionAdapter.class, platformAdapter);
    }

    /**
     * Picks the first registered adapter that is compatible with the running server; if none is,
     * the first one is returned so its compatibility error is reported at startup.
     */
    private PlatformSelection loadPlatformAdapter() {
        PlatformAdapter first = null;
        String firstError = null;
        Iterator<PlatformAdapter> adapters =
            ServiceLoader.load(PlatformAdapter.class, getClass().getClassLoader()).iterator();
        int skipped = 0;
        while (skipped < MAX_SKIPPED_ADAPTERS) {
            PlatformAdapter adapter;
            try {
                if (!adapters.hasNext()) {
                    break;
                }
                adapter = adapters.next();
            } catch (ServiceConfigurationError | LinkageError e) {
                // e.g. an adapter compiled for a newer Java than this server runs (the provider class is
                // loaded inside hasNext() on newer JDKs): skip it and try the next registered adapter
                ++skipped;
                continue;
            }
            String compatibilityError = adapter.getCompatibilityError();
            if (compatibilityError == null) {
                return new PlatformSelection(adapter, null);
            }
            if (first == null) {
                first = adapter;
                firstError = compatibilityError;
            }
        }
        if (first != null) {
            return new PlatformSelection(first, firstError);
        }
        throw new IllegalStateException(
            "No PlatformAdapter found. Ensure you are using a version-specific AuthMe jar.");
    }

    /**
     * The platform adapter chosen at startup together with the compatibility error determined while
     * selecting it, so the error is not computed a second time.
     */
    private static final class PlatformSelection {
        private final PlatformAdapter adapter;
        private final String compatibilityError;

        PlatformSelection(PlatformAdapter adapter, String compatibilityError) {
            this.adapter = adapter;
            this.compatibilityError = compatibilityError;
        }
    }

    /**
     * Instantiates all services.
     *
     * @param injector the injector
     */
    void instantiateServices(Injector injector) {
        database = injector.getSingleton(DataSource.class);
        bukkitService = injector.getSingleton(BukkitService.class);
        commandHandler = injector.getSingleton(CommandHandler.class);
        backupService = injector.getSingleton(BackupService.class);

        // Trigger instantiation (class not used elsewhere)
        injector.getSingleton(BungeeReceiver.class);

        // Trigger construction of API classes; they will keep track of the singleton
        injector.getSingleton(AuthMeApi.class);
    }

    /**
     * Registers all event listeners.
     *
     * @param injector the injector
     */
    void registerEventListeners(Injector injector) {
        PluginManager pluginManager = getServer().getPluginManager();
        EventRegistrationAdapter eventRegistration = injector.getSingleton(EventRegistrationAdapter.class);
        for (Class<? extends Listener> listenerClass : eventRegistration.getListeners()) {
            pluginManager.registerEvents(injector.getSingleton(listenerClass), this);
        }
    }

    /**
     * Stops the server or disables the plugin, as defined in the configuration.
     */
    public void stopOrUnload() {
        if (settings == null || settings.getProperty(SecuritySettings.STOP_SERVER_ON_PROBLEM)) {
            getLogger().warning("THE SERVER IS GOING TO SHUT DOWN AS DEFINED IN THE CONFIGURATION!");
            setEnabled(false);
            getServer().shutdown();
        } else {
            setEnabled(false);
        }
    }

    @Override
    public void onDisable() {
        // onDisable is also called when we prematurely abort, so any field may be null
        OnShutdownPlayerSaver onShutdownPlayerSaver = injector == null
            ? null
            : injector.createIfHasDependencies(OnShutdownPlayerSaver.class);
        if (onShutdownPlayerSaver != null) {
            onShutdownPlayerSaver.saveAllPlayers();
        }

        // Do backup on stop if enabled
        if (backupService != null) {
            backupService.doBackup(BackupService.BackupCause.STOP);
        }

        // Wait for tasks and close data source
        new TaskCloser(this, database).run();

        // Disabled correctly
        Consumer<String> infoLogMethod = logger == null ? getLogger()::info : logger::info;
        infoLogMethod.accept("AuthMe " + this.getDescription().getVersion() + " disabled!");
        ConsoleLogger.closeFileWriter();
    }

    /**
     * Handle Bukkit commands.
     *
     * @param sender       The command sender (Bukkit).
     * @param cmd          The command (Bukkit).
     * @param commandLabel The command label (Bukkit).
     * @param args         The command arguments (Bukkit).
     * @return True if the command was executed, false otherwise.
     */
    @Override
    public boolean onCommand(CommandSender sender, Command cmd,
                             String commandLabel, String[] args) {
        // Make sure the command handler has been initialized
        if (commandHandler == null) {
            getLogger().severe("AuthMe command handler is not available");
            return false;
        }

        // Handle the command
        return commandHandler.processCommand(sender, commandLabel, args);
    }

    private String getServerNameVersionSafe() {
        try {
            Server server = getServer();
            return server.getName() + " v. " + server.getVersion();
        } catch (Throwable ignore) {
            return "-";
        }
    }
}
