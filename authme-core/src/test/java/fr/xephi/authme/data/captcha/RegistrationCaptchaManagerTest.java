package fr.xephi.authme.data.captcha;

import fr.xephi.authme.ReflectionTestUtils;
import fr.xephi.authme.settings.Settings;
import fr.xephi.authme.settings.properties.SecuritySettings;
import fr.xephi.authme.util.expiring.ExpiringMap;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import static fr.xephi.authme.AuthMeMatchers.stringWithLength;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * Test for {@link RegistrationCaptchaManager}.
 */
class RegistrationCaptchaManagerTest {

    @Test
    void shouldBeDisabled() {
        // when
        RegistrationCaptchaManager captchaManager1 = new RegistrationCaptchaManager(mockSettings(false, 12));
        RegistrationCaptchaManager captchaManager2 = new RegistrationCaptchaManager(mockSettings(true, 12));

        // then
        assertThat(captchaManager1.isCaptchaRequired("bob"), equalTo(false));
        assertThat(captchaManager2.isCaptchaRequired("bob"), equalTo(true));
    }

    @Test
    void shouldVerifyCodeSuccessfully() {
        // given
        RegistrationCaptchaManager captchaManager = new RegistrationCaptchaManager(mockSettings(true, 12));
        String captcha = "abc3";
        getCodeMap(captchaManager).put("test", captcha);

        Player player = mock(Player.class);
        given(player.getName()).willReturn("TeSt");

        // when
        boolean isSuccessful = captchaManager.checkCode(player, captcha);

        // then
        assertThat(isSuccessful, equalTo(true));
        assertThat(getCodeMap(captchaManager).isEmpty(), equalTo(true));
        assertThat(captchaManager.isCaptchaRequired("test"), equalTo(false));
    }

    @Test
    void shouldGenerateAndRetrieveCode() {
        // given
        int captchaLength = 9;
        RegistrationCaptchaManager captchaManager = new RegistrationCaptchaManager(mockSettings(true, captchaLength));

        // when
        String captcha1 = captchaManager.getCaptchaCodeOrGenerateNew("toast");
        String captcha2 = captchaManager.getCaptchaCodeOrGenerateNew("Toast");

        // then
        assertThat(captcha1, equalTo(captcha2));
        assertThat(captcha1, stringWithLength(captchaLength));

        // given (2)
        Player player = mock(Player.class);
        given(player.getName()).willReturn("toast");

        // when (2) / then (2)
        assertThat(captchaManager.checkCode(player, captcha1), equalTo(true));
    }

    @SuppressWarnings("unchecked")
    private static ExpiringMap<String, String> getCodeMap(RegistrationCaptchaManager captchaManager) {
        CaptchaCodeStorage captchaStorage = ReflectionTestUtils.getFieldValue(
            RegistrationCaptchaManager.class, captchaManager, "captchaCodeStorage");
        return ReflectionTestUtils.getFieldValue(CaptchaCodeStorage.class, captchaStorage, "captchaCodes");
    }

    private static Settings mockSettings(boolean enabled, int captchaLength) {
        Settings settings = mock(Settings.class);
        given(settings.getProperty(SecuritySettings.ENABLE_CAPTCHA_FOR_REGISTRATION)).willReturn(enabled);
        given(settings.getProperty(SecuritySettings.CAPTCHA_LENGTH)).willReturn(captchaLength);
        return settings;
    }
}
