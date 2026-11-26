package fr.xephi.authme.security.crypts;

import ch.jalu.configme.properties.Property;
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
        return withProperty(SecuritySettings.PBKDF2_NUMBER_OF_ROUNDS, rounds);
    }

    static Settings withBcryptRounds(int rounds) {
        return withProperty(HooksSettings.BCRYPT_LOG2_ROUND, rounds);
    }

    static Settings withDoubleMd5SaltLength(int length) {
        return withProperty(SecuritySettings.DOUBLE_MD5_SALT_LENGTH, length);
    }

    private static <T> Settings withProperty(Property<T> property, T value) {
        Settings settings = mock(Settings.class);
        given(settings.getProperty(property)).willReturn(value);
        return settings;
    }
}
