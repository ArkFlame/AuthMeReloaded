package fr.xephi.authme.command.executable.captcha;

import fr.xephi.authme.data.auth.PlayerCache;
import fr.xephi.authme.data.captcha.LoginCaptchaManager;
import fr.xephi.authme.data.captcha.RegistrationCaptchaManager;
import fr.xephi.authme.data.limbo.LimboService;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.service.CommonService;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collections;

import static fr.xephi.authme.listener.TestPlayerUtils.mockPlayerWithName;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Test for {@link CaptchaCommand}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.WARN)
public class CaptchaCommandTest {

    @InjectMocks
    private CaptchaCommand command;

    @Mock
    private LoginCaptchaManager loginCaptchaManager;

    @Mock
    private RegistrationCaptchaManager registrationCaptchaManager;

    @Mock
    private PlayerCache playerCache;

    @Mock
    private CommonService commonService;

    @Mock
    private LimboService limboService;

    @Mock
    private DataSource dataSource;

    @Test
    public void shouldDetectIfPlayerIsLoggedIn() {
        // given
        String name = "creeper011";
        Player player = mockPlayerWithName(name);
        given(playerCache.isAuthenticated(name)).willReturn(true);

        // when
        command.executeCommand(player, Collections.singletonList("123"));

        // then
        verify(commonService).send(player, MessageKey.ALREADY_LOGGED_IN_ERROR);
    }

    @ParameterizedTest
    @CsvSource({"true, USAGE_LOGIN", "false, USAGE_REGISTER"})
    public void shouldShowUsageWhenNoCaptchaIsRequired(boolean registered, MessageKey expectedMessage) {
        // given
        String name = "player";
        Player player = mockPlayerWithName(name);
        given(playerCache.isAuthenticated(name)).willReturn(false);
        given(loginCaptchaManager.isCaptchaRequired(name)).willReturn(false);
        if (registered) {
            given(dataSource.isAuthAvailable(name)).willReturn(true);
        } else {
            given(registrationCaptchaManager.isCaptchaRequired(name)).willReturn(false);
        }

        // when
        command.executeCommand(player, Collections.singletonList("code"));

        // then
        verify(commonService).send(player, expectedMessage);
        if (registered) {
            verify(loginCaptchaManager).isCaptchaRequired(name);
            verifyNoMoreInteractions(loginCaptchaManager, registrationCaptchaManager);
        } else {
            verify(registrationCaptchaManager, only()).isCaptchaRequired(name);
        }
    }

    @ParameterizedTest
    @CsvSource({
        "true, CAPTCHA_SUCCESS, LOGIN_MESSAGE",
        "false, REGISTER_CAPTCHA_SUCCESS, REGISTER_MESSAGE"
    })
    public void shouldHandleCorrectCaptcha(boolean loginCaptcha, MessageKey successMessage,
                                           MessageKey nextStepMessage) {
        // given
        String name = "smith";
        Player player = mockPlayerWithName(name);
        given(playerCache.isAuthenticated(name)).willReturn(false);
        given(loginCaptchaManager.isCaptchaRequired(name)).willReturn(loginCaptcha);
        String captchaCode = "3991";
        if (loginCaptcha) {
            given(loginCaptchaManager.checkCode(player, captchaCode)).willReturn(true);
        } else {
            given(registrationCaptchaManager.isCaptchaRequired(name)).willReturn(true);
            given(registrationCaptchaManager.checkCode(player, captchaCode)).willReturn(true);
        }

        // when
        command.executeCommand(player, Collections.singletonList(captchaCode));

        // then
        verify(commonService).send(player, successMessage);
        verify(commonService).send(player, nextStepMessage);
        if (loginCaptcha) {
            verify(loginCaptchaManager).isCaptchaRequired(name);
            verify(loginCaptchaManager).checkCode(player, captchaCode);
            verifyNoMoreInteractions(loginCaptchaManager);
            verify(limboService).unmuteMessageTask(player);
            verifyNoMoreInteractions(commonService);
        } else {
            verify(registrationCaptchaManager).checkCode(player, captchaCode);
            verify(loginCaptchaManager, only()).isCaptchaRequired(name);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void shouldHandleWrongCaptcha(boolean loginCaptcha) {
        // given
        String name = "smith";
        Player player = mockPlayerWithName(name);
        given(loginCaptchaManager.isCaptchaRequired(name)).willReturn(loginCaptcha);
        String captchaCode = "2468";
        String newCode = "1337";
        if (loginCaptcha) {
            given(loginCaptchaManager.checkCode(player, captchaCode)).willReturn(false);
            given(loginCaptchaManager.getCaptchaCodeOrGenerateNew(name)).willReturn(newCode);
        } else {
            given(registrationCaptchaManager.isCaptchaRequired(name)).willReturn(true);
            given(registrationCaptchaManager.checkCode(player, captchaCode)).willReturn(false);
            given(registrationCaptchaManager.getCaptchaCodeOrGenerateNew(name)).willReturn(newCode);
        }

        // when
        command.executeCommand(player, Collections.singletonList(captchaCode));

        // then
        verify(commonService).send(player, MessageKey.CAPTCHA_WRONG_ERROR, newCode);
        if (loginCaptcha) {
            verify(loginCaptchaManager).isCaptchaRequired(name);
            verify(loginCaptchaManager).checkCode(player, captchaCode);
            verify(loginCaptchaManager).getCaptchaCodeOrGenerateNew(name);
            verifyNoMoreInteractions(loginCaptchaManager);
            verifyNoMoreInteractions(commonService);
        } else {
            verify(registrationCaptchaManager).checkCode(player, captchaCode);
            verify(registrationCaptchaManager).getCaptchaCodeOrGenerateNew(name);
        }
    }

}
