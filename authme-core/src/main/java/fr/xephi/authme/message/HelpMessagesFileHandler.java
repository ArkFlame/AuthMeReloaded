package fr.xephi.authme.message;

import fr.xephi.authme.ConsoleLogger;
import fr.xephi.authme.initialization.DataFolder;
import fr.xephi.authme.output.ConsoleLoggerFactory;
import fr.xephi.authme.settings.Settings;
import fr.xephi.authme.util.FileUtils;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import static fr.xephi.authme.message.MessagePathHelper.DEFAULT_LANGUAGE;

/**
 * File handler for the help_xx.yml resource.
 */
public class HelpMessagesFileHandler extends AbstractMessageFileHandler {

    private final ConsoleLogger logger = ConsoleLoggerFactory.get(HelpMessagesFileHandler.class);

    private FileConfiguration defaultConfiguration;

    @Inject
    public HelpMessagesFileHandler(@DataFolder File dataFolder, Settings settings) {
        super(dataFolder, settings);
    }

    @Override
    public String getMessage(String key) {
        String message = getMessageIfExists(key);
        if (message != null) {
            return message;
        }

        logger.warning("Error getting message with key '" + key + "'. "
            + "Please update your config file '" + getFilename() + "' or run /authme messages help");
        if (defaultConfiguration == null) {
            defaultConfiguration = loadDefaultConfiguration();
        }
        return defaultConfiguration.getString(key, "Error retrieving message '" + key + "'");
    }

    private FileConfiguration loadDefaultConfiguration() {
        String defaultPath = createFilePath(DEFAULT_LANGUAGE);
        InputStream stream = FileUtils.getResourceFromJar(defaultPath);
        if (stream == null) {
            logger.warning("Could not load default help messages file '" + defaultPath + "' from JAR");
            return new YamlConfiguration();
        }
        try (InputStreamReader reader = new InputStreamReader(stream)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (IOException e) {
            logger.logException("Could not read default help messages file '" + defaultPath + "'", e);
            return new YamlConfiguration();
        }
    }

    @Override
    protected String createFilePath(String language) {
        return MessagePathHelper.createHelpMessageFilePath(language);
    }
}
