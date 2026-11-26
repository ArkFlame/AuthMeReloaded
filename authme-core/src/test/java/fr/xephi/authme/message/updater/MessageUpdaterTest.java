package fr.xephi.authme.message.updater;

import ch.jalu.configme.properties.Property;
import ch.jalu.configme.resource.PropertyReader;
import ch.jalu.configme.resource.YamlFileReader;
import com.google.common.io.Files;
import fr.xephi.authme.TestHelper;
import fr.xephi.authme.message.MessageKey;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static fr.xephi.authme.TestHelper.createFile;
import static fr.xephi.authme.message.MessagePathHelper.DEFAULT_MESSAGES_FILE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * Test for {@link MessageUpdater}.
 */
class MessageUpdaterTest {

    private MessageUpdater messageUpdater = new MessageUpdater();

    @TempDir
    File temporaryFolder;

    @BeforeAll
    static void setUpLogger() {
        TestHelper.setupLogger();
    }

    private File copyMessagesFile(String name, String resourcePath) throws IOException {
        File messagesFile = createFile(temporaryFolder, name);
        Files.copy(TestHelper.getJarFile(resourcePath), messagesFile);
        return messagesFile;
    }

    @Test
    void shouldNotUpdateDefaultFile() throws IOException {
        String messagesFilePath = DEFAULT_MESSAGES_FILE;
        File messagesFile = copyMessagesFile("fffff", "/" + messagesFilePath);
        long modifiedDate = messagesFile.lastModified();

        boolean wasChanged = messageUpdater.migrateAndSave(messagesFile, messagesFilePath, messagesFilePath);

        assertThat(wasChanged, equalTo(false));
        assertThat(messagesFile.lastModified(), equalTo(modifiedDate));
    }

    @Test
    void shouldAddMissingKeys() throws IOException {
        File messagesFile = copyMessagesFile("file", TestHelper.PROJECT_ROOT + "message/messages_test.yml");

        boolean wasChanged = messageUpdater.migrateAndSave(messagesFile, "does-not-exist", DEFAULT_MESSAGES_FILE);

        assertThat(wasChanged, equalTo(true));
        PropertyReader reader = new YamlFileReader(messagesFile);
        // Existing keys should not be overridden
        assertThat(reader.getString(MessageKey.LOGIN_SUCCESS.getKey()), equalTo("&cHere we have&bdefined some colors &dand some other &lthings"));
        assertThat(reader.getString(MessageKey.EMAIL_ALREADY_USED_ERROR.getKey()), equalTo(""));
        // Check that new keys were added
        assertThat(reader.getString(MessageKey.SECOND.getKey()), equalTo("second"));
        assertThat(reader.getString(MessageKey.ERROR.getKey()), equalTo("&4An unexpected error occurred, please contact an administrator!"));
    }

    @Test
    void shouldMigrateOldEntries() throws IOException {
        File messagesFile = copyMessagesFile("messages.yml", TestHelper.PROJECT_ROOT + "message/messages_en_old.yml");

        boolean wasChanged = messageUpdater.migrateAndSave(messagesFile, DEFAULT_MESSAGES_FILE, DEFAULT_MESSAGES_FILE);

        assertThat(wasChanged, equalTo(true));
        PropertyReader reader = new YamlFileReader(messagesFile);
        assertThat(reader.getString(MessageKey.PASSWORD_MATCH_ERROR.getKey()),
            equalTo("Password error message"));
        assertThat(reader.getString(MessageKey.INVALID_NAME_CHARACTERS.getKey()),
            equalTo("not valid username: Allowed chars are %valid_chars"));
        assertThat(reader.getString(MessageKey.INVALID_OLD_EMAIL.getKey()),
            equalTo("Email (old) is not valid!!"));
        assertThat(reader.getString(MessageKey.CAPTCHA_WRONG_ERROR.getKey()),
            equalTo("The captcha code is %captcha_code for you"));
        assertThat(reader.getString(MessageKey.CAPTCHA_FOR_REGISTRATION_REQUIRED.getKey()),
            equalTo("Now type /captcha %captcha_code"));
        assertThat(reader.getString(MessageKey.SECONDS.getKey()),
            equalTo("seconds in plural"));
    }

    @Test
    void shouldPerformNewerMigrations() throws IOException {
        File messagesFile = copyMessagesFile("newFile", TestHelper.PROJECT_ROOT + "message/messages_test2.yml");

        boolean wasChanged = messageUpdater.migrateAndSave(messagesFile, DEFAULT_MESSAGES_FILE, DEFAULT_MESSAGES_FILE);

        assertThat(wasChanged, equalTo(true));
        PropertyReader reader = new YamlFileReader(messagesFile);
        assertThat(reader.getString(MessageKey.TWO_FACTOR_CREATE.getKey()), equalTo("Old 2fa create text"));
        assertThat(reader.getString(MessageKey.WRONG_PASSWORD.getKey()), equalTo("test2 - wrong password")); // from pre-5.5 key
        assertThat(reader.getString(MessageKey.SECOND.getKey()), equalTo("second")); // from messages_en.yml
    }

    @Test
    void shouldHaveAllKeysInConfigurationData() {
        Set<String> messageKeysFromEnum = Arrays.stream(MessageKey.values())
            .map(MessageKey::getKey)
            .collect(Collectors.toSet());

        Set<String> messageKeysFromConfigData = MessageUpdater.createConfigurationData().getProperties().stream()
            .map(Property::getPath)
            .collect(Collectors.toSet());

        assertThat(messageKeysFromConfigData, equalTo(messageKeysFromEnum));
    }

    @Test
    void shouldHaveCommentForAllRootPathsInConfigurationData() {
        Set<String> rootPaths = Arrays.stream(MessageKey.values())
            .map(key -> key.getKey().split("\\.")[0])
            .collect(Collectors.toSet());

        Map<String, List<String>> comments = MessageUpdater.createConfigurationData().getAllComments();

        assertThat(comments.keySet(), equalTo(rootPaths));
    }
}
