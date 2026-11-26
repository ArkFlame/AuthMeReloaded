package fr.xephi.authme.settings;

import ch.jalu.configme.configurationdata.ConfigurationData;
import ch.jalu.configme.configurationdata.ConfigurationDataBuilder;
import ch.jalu.configme.resource.PropertyReader;
import ch.jalu.configme.resource.PropertyResource;
import fr.xephi.authme.TestHelper;
import fr.xephi.authme.settings.properties.TestConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.function.Function;
import java.util.stream.Stream;

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

    @ParameterizedTest(name = "{0}")
    @MethodSource("emailMessages")
    void shouldLoadEmailMessage(String fileName, String emailMessage, Function<Settings, String> getter)
            throws IOException {
        // given
        Settings settings = createSettingsWithEmailFile(fileName, emailMessage);

        // when / then
        assertThat(getter.apply(settings), equalTo(emailMessage));
    }

    private static Stream<Arguments> emailMessages() {
        return Stream.of(
            Arguments.of("email.html", "Sample email message\nThat's all!",
                (Function<Settings, String>) Settings::getPasswordEmailMessage),
            Arguments.of("recovery_code_email.html", "Your recovery code is %code.",
                (Function<Settings, String>) Settings::getRecoveryCodeEmailMessage),
            Arguments.of("verification_code_email.html", "Please verify your identity with <recoverycode />.",
                (Function<Settings, String>) Settings::getVerificationEmailMessage));
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
