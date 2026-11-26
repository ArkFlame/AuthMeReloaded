package fr.xephi.authme.settings;

import ch.jalu.configme.configurationdata.ConfigurationData;
import ch.jalu.configme.configurationdata.ConfigurationDataBuilder;
import ch.jalu.configme.resource.PropertyReader;
import ch.jalu.configme.resource.PropertyResource;
import fr.xephi.authme.TestHelper;
import fr.xephi.authme.settings.properties.TestConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link Settings}.
 */
class SettingsTest {

    private static final ConfigurationData CONFIG_DATA =
        ConfigurationDataBuilder.createConfiguration(TestConfiguration.class);

    @TempDir
    File testPluginFolder;

    @BeforeAll
    static void setUpLogger() {
        TestHelper.setupLogger();
    }

    @Test
    void shouldLoadEmailMessage() throws IOException {
        // given
        String emailMessage = "Sample email message\nThat's all!";
        Settings settings = createSettingsWithEmailFile("email.html", emailMessage);

        // when
        String result = settings.getPasswordEmailMessage();

        // then
        assertThat(result, equalTo(emailMessage));
    }

    @Test
    void shouldLoadRecoveryCodeMessage() throws IOException {
        // given
        String emailMessage = "Your recovery code is %code.";
        Settings settings = createSettingsWithEmailFile("recovery_code_email.html", emailMessage);

        // when
        String result = settings.getRecoveryCodeEmailMessage();

        // then
        assertThat(result, equalTo(emailMessage));
    }

    @Test
    void shouldLoadVerificationMessage() throws IOException {
        // given
        String emailMessage = "Please verify your identity with <recoverycode />.";
        Settings settings = createSettingsWithEmailFile("verification_code_email.html", emailMessage);

        // when
        String result = settings.getVerificationEmailMessage();

        // then
        assertThat(result, equalTo(emailMessage));
    }

    private Settings createSettingsWithEmailFile(String fileName, String content) throws IOException {
        File emailFile = new File(testPluginFolder, fileName);
        createFile(emailFile);
        Files.write(emailFile.toPath(), content.getBytes());
        return new Settings(testPluginFolder, mockPropertyResourceAndReader(), null, CONFIG_DATA);
    }

    private static PropertyResource mockPropertyResourceAndReader() {
        PropertyReader reader = mock(PropertyReader.class, RETURNS_DEEP_STUBS);
        given(reader.getList(anyString())).willReturn(Collections.emptyList());
        PropertyResource resource = mock(PropertyResource.class);
        given(resource.createReader()).willReturn(reader);
        return resource;
    }

    private static void createFile(File file) {
        try {
            file.getParentFile().mkdirs();
            file.createNewFile();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

}
