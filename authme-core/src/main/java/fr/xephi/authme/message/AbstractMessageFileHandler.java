package fr.xephi.authme.message;

import com.google.common.annotations.VisibleForTesting;
import fr.xephi.authme.ConsoleLogger;
import fr.xephi.authme.initialization.DataFolder;
import fr.xephi.authme.initialization.Reloadable;
import fr.xephi.authme.output.ConsoleLoggerFactory;
import fr.xephi.authme.settings.Settings;
import fr.xephi.authme.settings.properties.PluginSettings;
import fr.xephi.authme.util.FileUtils;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import javax.annotation.PostConstruct;
import java.io.File;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static fr.xephi.authme.message.MessagePathHelper.DEFAULT_LANGUAGE;

/**
 * Handles a YAML message file with a default file fallback.
 */
public abstract class AbstractMessageFileHandler implements Reloadable {

    private static final FileConfiguration UNAVAILABLE = new YamlConfiguration();
    /** Cap for cached "no such language" results, so client-supplied junk locales cannot grow the cache. */
    private static final int MAX_CACHED_UNAVAILABLE = 64;

    private final ConsoleLogger logger = ConsoleLoggerFactory.get(AbstractMessageFileHandler.class);

    private final File dataFolder;
    private final Settings settings;

    private volatile String filename;
    private volatile FileConfiguration configuration;
    private final String defaultFile;
    private final ConcurrentHashMap<String, FileConfiguration> languageCache = new ConcurrentHashMap<>();
    private final Function<String, FileConfiguration> languageLoader = language -> {
        File file = getMessageFile(createFilePath(language));
        return file.exists() ? YamlConfiguration.loadConfiguration(file) : UNAVAILABLE;
    };

    protected AbstractMessageFileHandler(@DataFolder File dataFolder, Settings settings) {
        this.dataFolder = dataFolder;
        this.settings = settings;
        this.defaultFile = createFilePath(DEFAULT_LANGUAGE);
    }

    @Override
    @PostConstruct
    public void reload() {
        filename = createFilePath(getLanguage());
        File messagesFile = initializeFile(filename);
        // An empty configuration keeps lookups on the normal missing-key path if no file could be resolved.
        configuration = messagesFile == null ? new YamlConfiguration() : YamlConfiguration.loadConfiguration(messagesFile);
        languageCache.clear();
    }

    protected String getLanguage() {
        return settings.getProperty(PluginSettings.MESSAGES_LANGUAGE);
    }

    protected File getUserLanguageFile() {
        return new File(dataFolder, filename);
    }

    protected String getFilename() {
        return filename;
    }

    /**
     * Returns whether the message file configuration has an entry at the given path.
     *
     * @param path the path to verify
     * @return true if an entry exists for the path in the messages file, false otherwise
     */
    public boolean hasSection(String path) {
        return configuration.get(path) != null;
    }

    /**
     * Returns the message for the given key.
     *
     * @param key the key to retrieve the message for
     * @return the message
     */
    public String getMessage(String key) {
        String message = getMessageIfExists(key);
        return message == null ? "Error retrieving message '" + key + "'" : message;
    }

    /**
     * Returns the message for the given key only if it exists,
     * i.e. without falling back to the default file.
     *
     * @param key the key to retrieve the message for
     * @return the message, or {@code null} if not available
     */
    public String getMessageIfExists(String key) {
        return configuration.getString(key);
    }

    /**
     * Returns the message for the given key in the requested language only if it exists,
     * falling back to the server-configured language. Returns {@code null} if not found in either.
     *
     * @param key the key to retrieve the message for
     * @param language the AuthMe language code (e.g. {@code "fr"}), or {@code null} to use the server default
     * @return the message, or {@code null} if not available
     */
    public String getMessageIfExists(String key, String language) {
        String message = getLocalizedMessage(key, language);
        return message != null ? message : getMessageIfExists(key);
    }

    /**
     * Returns the message for the given key in the requested language, falling back to the
     * server-configured language if the requested language is unavailable or missing the key.
     *
     * @param key the key to retrieve the message for
     * @param language the AuthMe language code (e.g. {@code "fr"}), or {@code null} to use the server default
     * @return the message
     */
    public String getMessage(String key, String language) {
        String message = getLocalizedMessage(key, language);
        return message != null ? message : getMessage(key);
    }

    /**
     * Looks up a key in the requested language only, without any fallback. Returns {@code null}
     * when the requested language is the server default, could not be loaded, or lacks the key.
     * Callers apply the fallback so it is evaluated lazily and no supplier is allocated.
     *
     * @param key the key to retrieve
     * @param language the requested language, or null for the server default
     * @return the message in the requested language, or null if not available
     */
    private String getLocalizedMessage(String key, String language) {
        if (language == null || language.equals(getLanguage())) {
            return null;
        }
        FileConfiguration config = languageCache.get(language);
        if (config == null) {
            config = languageLoader.apply(language);
            if (config != UNAVAILABLE || languageCache.size() < MAX_CACHED_UNAVAILABLE) {
                languageCache.putIfAbsent(language, config);
            }
        }
        return config == UNAVAILABLE ? null : config.getString(key);
    }

    /**
     * Returns the message file for the given path, copying it from the JAR if it is missing.
     *
     * @param filePath path to the message file
     * @return the message file (which may not exist if it could not be copied)
     */
    private File getMessageFile(String filePath) {
        File file = new File(dataFolder, filePath);
        if (!file.exists()) {
            try (java.io.InputStream resource = FileUtils.getResourceFromJar(filePath)) {
                if (resource != null) {
                    FileUtils.copyFileFromResource(file, filePath);
                }
            } catch (java.io.IOException ignored) {
                // closing the probe stream failed: nothing to recover
            }
        }
        return file;
    }

    /**
     * Creates the path to the messages file for the given language code.
     *
     * @param language the language code
     * @return path to the message file for the given language
     */
    protected abstract String createFilePath(String language);

    /**
     * Copies the messages file from the JAR to the local messages/ folder if it doesn't exist.
     *
     * @param filePath path to the messages file to use
     * @return the messages file to use
     */
    @VisibleForTesting
    File initializeFile(String filePath) {
        File file = getMessageFile(filePath);
        if (file.exists() || FileUtils.copyFileFromResource(file, defaultFile)) {
            return file;
        }
        logger.warning("Wanted to copy default messages file '" + defaultFile + "' from JAR but it didn't exist");
        return null;
    }
}
