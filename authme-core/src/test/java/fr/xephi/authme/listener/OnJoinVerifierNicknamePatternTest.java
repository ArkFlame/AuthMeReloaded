package fr.xephi.authme.listener;

import fr.xephi.authme.TestHelper;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.message.Messages;
import fr.xephi.authme.permission.PermissionsManager;
import fr.xephi.authme.service.AntiBotService;
import fr.xephi.authme.service.BukkitService;
import fr.xephi.authme.service.ValidationService;
import fr.xephi.authme.settings.Settings;
import fr.xephi.authme.settings.properties.RestrictionSettings;
import org.bukkit.Server;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

/**
 * Test for the nickname pattern handling of {@link OnJoinVerifier}.
 */
@ExtendWith(MockitoExtension.class)
class OnJoinVerifierNicknamePatternTest {

    @InjectMocks
    private OnJoinVerifier onJoinVerifier;

    @Mock
    private Settings settings;
    @Mock
    private DataSource dataSource;
    @Mock
    private Messages messages;
    @Mock
    private PermissionsManager permissionsManager;
    @Mock
    private AntiBotService antiBotService;
    @Mock
    private ValidationService validationService;
    @Mock
    private BukkitService bukkitService;
    @Mock
    private Server server;

    @BeforeAll
    static void setUpLogger() {
        TestHelper.setupLogger();
    }

    @Test
    void shouldAllowNonAsciiNameWithSingleDotPattern() {
        // given
        configureNicknamePattern(".");

        // when / then
        assertDoesNotThrow(() -> onJoinVerifier.checkIsValidName("中文"));
    }

    @Test
    void shouldAllowBedrockNameWithDotStarPattern() {
        // given
        configureNicknamePattern(".*");

        // when / then
        assertDoesNotThrow(() -> onJoinVerifier.checkIsValidName("中文Player"));
    }

    @Test
    void shouldStillRejectInvalidCharactersWithRestrictivePattern() {
        // given
        configureNicknamePattern("[a-zA-Z0-9]+");

        // when / then
        assertThrows(FailedVerificationException.class, () -> onJoinVerifier.checkIsValidName("Tester!"));
    }

    private void configureNicknamePattern(String pattern) {
        given(settings.getProperty(RestrictionSettings.MIN_NICKNAME_LENGTH)).willReturn(1);
        given(settings.getProperty(RestrictionSettings.MAX_NICKNAME_LENGTH)).willReturn(16);
        given(settings.getProperty(RestrictionSettings.ALLOWED_NICKNAME_CHARACTERS)).willReturn(pattern);
        onJoinVerifier.reload();
    }
}
