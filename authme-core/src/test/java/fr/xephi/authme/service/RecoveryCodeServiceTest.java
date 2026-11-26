package fr.xephi.authme.service;

import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;

import fr.xephi.authme.ReflectionTestUtils;
import fr.xephi.authme.settings.Settings;
import fr.xephi.authme.settings.properties.SecuritySettings;
import fr.xephi.authme.util.expiring.ExpiringMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static fr.xephi.authme.AuthMeMatchers.stringWithLength;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.BDDMockito.given;

/**
 * Test for {@link RecoveryCodeService}.
 */
@ExtendWith(MockitoExtension.class)
class RecoveryCodeServiceTest {

    private RecoveryCodeService recoveryCodeService;

    @Mock
    private Settings settings;

    @BeforeEach
    void initService() {
        given(settings.getProperty(SecuritySettings.RECOVERY_CODE_HOURS_VALID)).willReturn(4);
        given(settings.getProperty(SecuritySettings.RECOVERY_CODE_LENGTH)).willReturn(5);
        given(settings.getProperty(SecuritySettings.RECOVERY_CODE_MAX_TRIES)).willReturn(3);

        recoveryCodeService = new RecoveryCodeService(settings);
    }

    @Test
    void shouldBeDisabledForNonPositiveLength() {
        assertThat(recoveryCodeService.isRecoveryCodeNeeded(), equalTo(true));

        given(settings.getProperty(SecuritySettings.RECOVERY_CODE_LENGTH)).willReturn(0);
        recoveryCodeService.reload(settings);

        assertThat(recoveryCodeService.isRecoveryCodeNeeded(), equalTo(false));
    }

    @Test
    void shouldGenerateStoreAndRecognizeCode() {
        String player = "dragon";
        String code = recoveryCodeService.generateCode(player);

        assertThat(getCodeMap().get(player), stringWithLength(5));
        assertThat(recoveryCodeService.isCodeValid(player, code), equalTo(true));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 3})
    void playerTriesLeft(int invalidAttempts) {
        String player = "Dusty";
        recoveryCodeService.generateCode(player);
        for (int i = 0; i < invalidAttempts; ++i) {
            recoveryCodeService.isCodeValid(player, i + "th try");
        }

        assertThat(recoveryCodeService.hasTriesLeft(player), equalTo(invalidAttempts == 0));
    }

    @Test
    void shouldRemoveCode() {
        String player = "Tester";
        String code = recoveryCodeService.generateCode(player);

        recoveryCodeService.removeCode(player);

        assertThat(recoveryCodeService.isCodeValid(player, code), equalTo(false));
        assertThat(getCodeMap().get(player), nullValue());
        assertThat(getTriesCounter().get(player), equalTo(0));
    }

    @Test
    void shouldUpdateBothCodeAndTryExpirationsUponReload() {
        assertThat(expirationMillis(getCodeMap()), equalTo(TimeUnit.HOURS.toMillis(4)));
        assertThat(expirationMillis(getTriesCounter()), equalTo(TimeUnit.HOURS.toMillis(4)));

        given(settings.getProperty(SecuritySettings.RECOVERY_CODE_HOURS_VALID)).willReturn(24);
        recoveryCodeService.reload(settings);

        assertThat(expirationMillis(getCodeMap()), equalTo(TimeUnit.HOURS.toMillis(24)));
        assertThat(expirationMillis(getTriesCounter()), equalTo(TimeUnit.HOURS.toMillis(24)));
    }

    private ExpiringMap<String, String> getCodeMap() {
        return ReflectionTestUtils.getFieldValue(RecoveryCodeService.class, recoveryCodeService, "recoveryCodes");
    }

    private ExpiringMap<String, Integer> getTriesCounter() {
        return ReflectionTestUtils.getFieldValue(RecoveryCodeService.class, recoveryCodeService, "playerTries");
    }

    private long expirationMillis(Object expiringStructure) {
        Class<?> clazz = expiringStructure.getClass();
        while (clazz != null) {
            try {
                Field field = clazz.getDeclaredField("expirationMillis");
                return (Long) ReflectionTestUtils.getFieldValue(field, expiringStructure);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        throw new IllegalStateException("Could not find expirationMillis field");
    }
}
