package fr.xephi.authme.message;

import fr.xephi.authme.settings.properties.PluginSettings;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

/**
 * Test for {@link MessagePathHelper}.
 */
class MessagePathHelperTest {

    private static final String[] INVALID_FILE_NAMES = {
        "messages/messages_fr.yml", "Messages_fr.yml", "otherfile.txt", "messages_de.txt", ""};

    @Test
    void shouldHaveLanguageInSyncWithConfigurations() {
        assertThat(MessagePathHelper.DEFAULT_LANGUAGE, equalTo(PluginSettings.MESSAGES_LANGUAGE.getDefaultValue()));
        assertThat(MessagePathHelper.DEFAULT_MESSAGES_FILE, equalTo(MessagePathHelper.createMessageFilePath(MessagePathHelper.DEFAULT_LANGUAGE)));
    }

    @Test
    void shouldBuildTextFilePaths() {
        assertThat(MessagePathHelper.createMessageFilePath("qq"), equalTo(MessagePathHelper.MESSAGES_FOLDER + "messages_qq.yml"));
        assertThat(MessagePathHelper.createHelpMessageFilePath("qq"), equalTo(MessagePathHelper.MESSAGES_FOLDER + "help_qq.yml"));
    }

    @Test
    void shouldRecognizeIfIsMessagesFile() {
        assertThat(MessagePathHelper.isMessagesFile("messages_nl.yml"), equalTo(true));
        assertThat(MessagePathHelper.isMessagesFile("messages_testtest.yml"), equalTo(true));
        for (String fileName : INVALID_FILE_NAMES) {
            assertThat(MessagePathHelper.isMessagesFile(fileName), equalTo(false));
        }
    }

    @Test
    void shouldReturnLanguageForMessagesFile() {
        assertThat(MessagePathHelper.getLanguageIfIsMessagesFile("messages_nl.yml"), equalTo("nl"));
        assertThat(MessagePathHelper.getLanguageIfIsMessagesFile("messages_testtest.yml"), equalTo("testtest"));
        for (String fileName : INVALID_FILE_NAMES) {
            assertThat(MessagePathHelper.getLanguageIfIsMessagesFile(fileName), nullValue());
        }
    }

    @Test
    void shouldRecognizeIfIsHelpFile() {
        assertThat(MessagePathHelper.isHelpFile("help_nl.yml"), equalTo(true));
        assertThat(MessagePathHelper.isHelpFile("help_testtest.yml"), equalTo(true));
        for (String fileName : INVALID_FILE_NAMES) {
            assertThat(MessagePathHelper.isHelpFile(fileName), equalTo(false));
        }
    }
}
