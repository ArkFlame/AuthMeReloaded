package fr.xephi.authme.security.crypts;
import fr.xephi.authme.settings.Settings;
import fr.xephi.authme.settings.properties.HooksSettings;
import fr.xephi.authme.settings.properties.SecuritySettings;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
/**
 * Shared settings mocks for encryption method tests.
 */
final class CryptTestSettings {
    private CryptTestSettings() {
    }
    static Settings withPbkdf2Rounds(int rounds) {
        Settings settings = mock(Settings.class);
        given(settings.getProperty(SecuritySettings.PBKDF2_NUMBER_OF_ROUNDS)).willReturn(rounds);
        return settings;
    }
    static Settings withBcryptRounds(int rounds) {
        Settings settings = mock(Settings.class);
        given(settings.getProperty(HooksSettings.BCRYPT_LOG2_ROUND)).willReturn(rounds);
        return settings;
    }
    static Settings withDoubleMd5SaltLength(int length) {
        Settings settings = mock(Settings.class);
        given(settings.getProperty(SecuritySettings.DOUBLE_MD5_SALT_LENGTH)).willReturn(length);
        return settings;
    }
}
