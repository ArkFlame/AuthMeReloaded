package fr.xephi.authme.message;

import fr.xephi.authme.ConsoleLogger;
import fr.xephi.authme.initialization.DataFolder;
import fr.xephi.authme.output.ConsoleLoggerFactory;
import fr.xephi.authme.message.updater.MessageUpdater;
import fr.xephi.authme.settings.Settings;

import javax.inject.Inject;

import java.io.File;

import static fr.xephi.authme.message.MessagePathHelper.DEFAULT_LANGUAGE;

/**
 * File handler for the messages_xx.yml resource.
 */
public class MessagesFileHandler extends AbstractMessageFileHandler {

    private final ConsoleLogger logger = ConsoleLoggerFactory.get(MessagesFileHandler.class);

    private final MessageUpdater messageUpdater;

    @Inject
    MessagesFileHandler(@DataFolder File dataFolder, Settings settings, MessageUpdater messageUpdater) {
        super(dataFolder, settings);
        this.messageUpdater = messageUpdater;
    }

    @Override
    public void reload() {
        // A migration may rewrite the user's file, so load it again before using it. If a second
        // migration is still needed, warn instead of retrying indefinitely.
        for (int attempt = 0; attempt < 2; ++attempt) {
            super.reload();
            boolean hasChange = messageUpdater.migrateAndSave(
                getUserLanguageFile(), createFilePath(getLanguage()), createFilePath(DEFAULT_LANGUAGE));
            if (!hasChange) {
                return;
            }
            if (attempt == 1) {
                logger.warning("Migration after reload attempt");
            }
        }
    }

    @Override
    protected String createFilePath(String language) {
        return MessagePathHelper.createMessageFilePath(language);
    }
}
