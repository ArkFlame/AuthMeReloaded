package fr.xephi.authme.settings;

import fr.xephi.authme.TestHelper;
import fr.xephi.authme.settings.properties.EmailSettings;
import fr.xephi.authme.settings.properties.PluginSettings;
import fr.xephi.authme.settings.properties.RestrictionSettings;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.logging.Logger;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Test for {@link SettingsWarner}.
 */
@ExtendWith(MockitoExtension.class)
class SettingsWarnerTest {

    @InjectMocks
    private SettingsWarner settingsWarner;

    @Mock
    private Settings settings;

    @ParameterizedTest
    @CsvSource({
        "false, 44, false, true,  -5, 3",
        "true,  25, false, false,  0, 0"
    })
    void shouldLogExpectedWarnings(boolean forceSingleSession, int smtpPort, boolean useTls,
                                   boolean sessionsEnabled, int sessionsTimeout, int expectedWarnings) {
        Logger logger = TestHelper.setupLogger();
        given(settings.getProperty(RestrictionSettings.FORCE_SINGLE_SESSION)).willReturn(forceSingleSession);
        given(settings.getProperty(EmailSettings.SMTP_PORT)).willReturn(smtpPort);
        given(settings.getProperty(EmailSettings.PORT25_USE_TLS)).willReturn(useTls);
        given(settings.getProperty(PluginSettings.SESSIONS_ENABLED)).willReturn(sessionsEnabled);
        if (sessionsEnabled) {
            given(settings.getProperty(PluginSettings.SESSIONS_TIMEOUT)).willReturn(sessionsTimeout);
        }

        settingsWarner.logWarningsForMisconfigurations();

        if (expectedWarnings == 0) {
            verifyNoInteractions(logger);
        } else {
            verify(logger, times(expectedWarnings)).warning(anyString());
        }
    }
}
