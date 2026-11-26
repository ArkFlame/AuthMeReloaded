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

    /**
     * Returns the message for the given key.
     *
     * @param key the key to retrieve the message for
     * @return the message
     */
    @Override
    public String getMessage(String key) {
        String message = getMessageIfExists(key);

        if (message == null) {
            logger.warning("Error getting message with key '" + key + "'. "
                + "Please update your config file '" + getFilename() + "' or run /authme messages help");
            return getDefault(key);
        }
        return message;
    }

    /**
     * Gets the message from the default file.
     *
     * @param key the key to retrieve the message for
     * @return the message from the default file
     */
    private String getDefault(String key) {
        if (defaultConfiguration == null) {
            String defaultPath = createFilePath(DEFAULT_LANGUAGE);
            InputStream stream = FileUtils.getResourceFromJar(defaultPath);
            if (stream == null) {
                logger.warning("Could not load default help messages file '" + defaultPath + "' from JAR");
                defaultConfiguration = new YamlConfiguration();
            } else {
                try (InputStreamReader reader = new InputStreamReader(stream)) {
                    defaultConfiguration = YamlConfiguration.loadConfiguration(reader);
                } catch (IOException e) {
                    logger.logException("Could not read default help messages file '" + defaultPath + "'", e);
                    defaultConfiguration = new YamlConfiguration();
                }
            }
        }
        String message = defaultConfiguration.getString(key);
        return message != null ? message : "Error retrieving message '" + key + "'";
    }

    @Override
    protected String createFilePath(String language) {
        return MessagePathHelper.createHelpMessageFilePath(language);
    }
}
