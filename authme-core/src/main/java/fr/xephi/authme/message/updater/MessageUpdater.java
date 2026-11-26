package fr.xephi.authme.message.updater;

import ch.jalu.configme.configurationdata.ConfigurationData;
import ch.jalu.configme.configurationdata.PropertyListBuilder;
import ch.jalu.configme.properties.Property;
import ch.jalu.configme.properties.StringProperty;
import ch.jalu.configme.properties.convertresult.ConvertErrorRecorder;
import ch.jalu.configme.resource.PropertyReader;
import ch.jalu.configme.resource.PropertyResource;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import fr.xephi.authme.ConsoleLogger;
import fr.xephi.authme.output.ConsoleLoggerFactory;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.util.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

import static java.util.Collections.singletonList;

/**
 * Migrates the used messages file to a complete, up-to-date version when necessary.
 */
public class MessageUpdater {

    private final ConsoleLogger logger = ConsoleLoggerFactory.get(MessageUpdater.class);

    /**
     * Applies any necessary migrations to the user's messages file and saves it if it has been modified.
     *
     * @param userFile the user's messages file (yml file in the plugin's folder)
     * @param localJarPath path to the messages file in the JAR for the same language (may not exist)
     * @param defaultJarPath path to the messages file in the JAR for the default language
     * @return true if the file has been migrated and saved, false if it is up-to-date
     */
    public boolean migrateAndSave(File userFile, String localJarPath, String defaultJarPath) {
        JarMessageSource jarMessageSource = new JarMessageSource(localJarPath, defaultJarPath);
        return migrateAndSave(userFile, jarMessageSource);
    }

    /**
     * Performs the migration.
     *
     * @param userFile the file to verify and migrate
     * @param jarMessageSource jar message source to get texts from if missing
     * @return true if the file has been migrated and saved, false if it is up-to-date
     */
    private boolean migrateAndSave(File userFile, JarMessageSource jarMessageSource) {
        // YamlConfiguration escapes all special characters when saving, making the file hard to use, so use ConfigMe
        MessageKeyConfigurationData configurationData = createConfigurationData();
        PropertyResource userResource = new MigraterYamlFileResource(userFile);

        PropertyReader reader = userResource.createReader();
        configurationData.initializeValues(reader);

        // Step 1: Migrate any old keys in the file to the new paths
        boolean movedOldKeys = OldMessageKeysMigrater.migrateOldPaths(reader, configurationData);
        if (movedOldKeys) {
            logger.info("Old keys have been moved to the new ones in your messages_xx.yml file");
        }
        // Step 2: Perform newer migrations
        boolean movedNewerKeys = moveIfApplicable(reader, configurationData, "misc.two_factor_create",
            MessageKey.TWO_FACTOR_CREATE, UnaryOperator.identity());
        // Step 3: Take any missing messages from the message files shipped in the AuthMe JAR
        boolean addedMissingKeys = addMissingKeys(jarMessageSource, configurationData);

        if (movedOldKeys || movedNewerKeys || addedMissingKeys) {
            backupMessagesFile(userFile);

            userResource.exportProperties(configurationData);
            logger.debug("Successfully saved {0}", userFile);
            return true;
        }
        return false;
    }

    /**
     * Moves a message from an old path to its new key, if the new key has no value yet.
     *
     * @param reader the reader to get the old value from
     * @param configurationData the configuration data to write the value to
     * @param oldPath the path of the old message
     * @param messageKey the key to move the message to
     * @param textTransform transformation to apply to the old text before storing it
     * @return true if a value was moved, false otherwise
     */
    static boolean moveIfApplicable(PropertyReader reader, MessageKeyConfigurationData configurationData,
                                    String oldPath, MessageKey messageKey, UnaryOperator<String> textTransform) {
        if (configurationData.getMessage(messageKey) != null) {
            return false;
        }
        String oldValue = reader.getString(oldPath);
        if (oldValue == null) {
            return false;
        }
        configurationData.setMessage(messageKey, textTransform.apply(oldValue));
        return true;
    }

    private boolean addMissingKeys(JarMessageSource jarMessageSource, MessageKeyConfigurationData configurationData) {
        List<String> addedKeys = new ArrayList<>();
        for (Property<String> property : configurationData.getAllMessageProperties()) {
            if (configurationData.getValue(property) == null) {
                String message = jarMessageSource.getMessageFromJar(property);
                // A key missing from both the local and the default JAR file must stay unset:
                // storing null would make every reload re-detect it as missing and rewrite the file.
                if (message != null) {
                    configurationData.setValue(property, message);
                    addedKeys.add(property.getPath());
                }
            }
        }
        if (addedKeys.isEmpty()) {
            return false;
        }
        logger.info(
            "Added " + addedKeys.size() + " missing keys to your messages_xx.yml file: " + addedKeys);
        return true;
    }

    private static void backupMessagesFile(File messagesFile) {
        String backupName = FileUtils.createBackupFilePath(messagesFile);
        File backupFile = new File(backupName);
        try {
            Files.copy(messagesFile, backupFile);
        } catch (IOException e) {
            throw new IllegalStateException("Could not back up '" + messagesFile + "' to '" + backupFile + "'", e);
        }
    }

    /**
     * Constructs the {@link ConfigurationData} for exporting a messages file in its entirety.
     *
     * @return the configuration data to export with
     */
    public static MessageKeyConfigurationData createConfigurationData() {
        Map<String, String> comments = ImmutableMap.<String, String>builder()
            .put("registration", "Registration")
            .put("password", "Password errors on registration")
            .put("login", "Login")
            .put("error", "Errors")
            .put("antibot", "AntiBot")
            .put("unregister", "Unregister")
            .put("misc", "Other messages")
            .put("session", "Session messages")
            .put("on_join_validation", "Error messages when joining")
            .put("login_location_fix", "Login location fix")
            .put("email", "Email")
            .put("recovery", "Password recovery by email")
            .put("captcha", "Captcha")
            .put("verification", "Verification code")
            .put("time", "Time units")
            .put("two_factor", "Two-factor authentication")
            .put("dialog", "Dialog UI")
            .put("command", "Command validation messages")
            .put("admin", "Admin command messages")
            .put("premium", "Premium mode")
            .build();

        Set<MessageKey> addedKeys = new HashSet<>();
        Map<String, List<String>> commentsMap = new HashMap<>();
        MessageKeyPropertyListBuilder builder = new MessageKeyPropertyListBuilder();
        MessageKey[] allKeys = MessageKey.values();
        // Add one key per section based on the comments map above so that the order is clear
        for (Map.Entry<String, String> comment : comments.entrySet()) {
            String path = comment.getKey();
            String prefix = path + ".";
            MessageKey firstKey = Arrays.stream(allKeys)
                .filter(key -> key.getKey().startsWith(prefix))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(path));
            builder.addMessageKey(firstKey);
            addedKeys.add(firstKey);
            commentsMap.put(path, singletonList(comment.getValue()));
        }
        // Add all remaining keys to the property list builder
        for (MessageKey key : allKeys) {
            if (!addedKeys.contains(key)) {
                builder.addMessageKey(key);
            }
        }
        return new MessageKeyConfigurationData(builder, commentsMap);
    }

    static final class MessageKeyProperty extends StringProperty {

        MessageKeyProperty(MessageKey messageKey) {
            super(messageKey.getKey(), "");
        }

        @Override
        protected String getFromReader(PropertyReader reader, ConvertErrorRecorder errorRecorder) {
            return reader.getString(getPath());
        }
    }

    static final class MessageKeyPropertyListBuilder {

        private final PropertyListBuilder propertyListBuilder = new PropertyListBuilder();

        void addMessageKey(MessageKey key) {
            propertyListBuilder.add(new MessageKeyProperty(key));
        }

        @SuppressWarnings("unchecked")
        List<MessageKeyProperty> getAllProperties() {
            return (List) propertyListBuilder.create();
        }
    }
}
