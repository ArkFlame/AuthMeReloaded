package fr.xephi.authme.listener;

import fr.xephi.authme.TestHelper;
import fr.xephi.authme.settings.Settings;
import fr.xephi.authme.settings.properties.RestrictionSettings;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.stream.Stream;

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

    @BeforeAll
    static void setUpLogger() {
        TestHelper.setupLogger();
    }

    @ParameterizedTest(name = "{0} allows {1}")
    @MethodSource("allowedNicknames")
    void shouldAllowNameMatchingPattern(String pattern, String name) {
        configureNicknamePattern(pattern);

        assertDoesNotThrow(() -> onJoinVerifier.checkIsValidName(name));
    }

    static Stream<Arguments> allowedNicknames() {
        return Stream.of(
            Arguments.of(".", "中文"),
            Arguments.of(".*", "中文Player"));
    }

    @Test
    void shouldStillRejectInvalidCharactersWithRestrictivePattern() {
        configureNicknamePattern("[a-zA-Z0-9]+");

        assertThrows(FailedVerificationException.class, () -> onJoinVerifier.checkIsValidName("Tester!"));
    }

    private void configureNicknamePattern(String pattern) {
        given(settings.getProperty(RestrictionSettings.MIN_NICKNAME_LENGTH)).willReturn(1);
        given(settings.getProperty(RestrictionSettings.MAX_NICKNAME_LENGTH)).willReturn(16);
        given(settings.getProperty(RestrictionSettings.ALLOWED_NICKNAME_CHARACTERS)).willReturn(pattern);
        onJoinVerifier.reload();
    }
}
