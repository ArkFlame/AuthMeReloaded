package fr.xephi.authme.command.executable.captcha;

import fr.xephi.authme.command.PlayerCommand;
import fr.xephi.authme.data.auth.PlayerCache;
import fr.xephi.authme.data.captcha.CaptchaManager;
import fr.xephi.authme.data.captcha.LoginCaptchaManager;
import fr.xephi.authme.data.captcha.RegistrationCaptchaManager;
import fr.xephi.authme.data.limbo.LimboMessageType;
import fr.xephi.authme.data.limbo.LimboService;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.service.CommonService;
import org.bukkit.entity.Player;

import javax.inject.Inject;
import java.util.List;

/**
 * Captcha command, allowing a player to solve a captcha.
 */
public class CaptchaCommand extends PlayerCommand {

    @Inject
    private PlayerCache playerCache;

    @Inject
    private LoginCaptchaManager loginCaptchaManager;

    @Inject
    private RegistrationCaptchaManager registrationCaptchaManager;

    @Inject
    private CommonService commonService;

    @Inject
    private LimboService limboService;

    @Inject
    private DataSource dataSource;

    @Override
    public void runCommand(Player player, List<String> arguments) {
        final String name = player.getName();

        if (playerCache.isAuthenticated(name)) {
            // No captcha is relevant if the player is logged in
            commonService.send(player, MessageKey.ALREADY_LOGGED_IN_ERROR);
            return;
        }

        if (loginCaptchaManager.isCaptchaRequired(name)) {
            if (checkCaptcha(player, arguments.get(0), loginCaptchaManager,
                MessageKey.CAPTCHA_SUCCESS, MessageKey.LOGIN_MESSAGE)) {
                limboService.unmuteMessageTask(player);
            }
        } else {
            final boolean isPlayerRegistered = dataSource.isAuthAvailable(name);
            if (!isPlayerRegistered && registrationCaptchaManager.isCaptchaRequired(name)) {
                checkCaptcha(player, arguments.get(0), registrationCaptchaManager,
                    MessageKey.REGISTER_CAPTCHA_SUCCESS, MessageKey.REGISTER_MESSAGE);
                limboService.resetMessageTask(player, LimboMessageType.REGISTER);
            } else {
                MessageKey errorMessage = isPlayerRegistered ? MessageKey.USAGE_LOGIN : MessageKey.USAGE_REGISTER;
                commonService.send(player, errorMessage);
            }
        }
    }

    private boolean checkCaptcha(Player player, String captchaCode, CaptchaManager captchaManager,
                                 MessageKey successMessage, MessageKey nextStepMessage) {
        if (captchaManager.checkCode(player, captchaCode)) {
            commonService.send(player, successMessage);
            commonService.send(player, nextStepMessage);
            return true;
        }
        sendWrongCaptchaError(player, captchaManager);
        return false;
    }

    private void sendWrongCaptchaError(Player player, CaptchaManager captchaManager) {
        String newCode = captchaManager.getCaptchaCodeOrGenerateNew(player.getName());
        commonService.send(player, MessageKey.CAPTCHA_WRONG_ERROR, newCode);
    }
}
