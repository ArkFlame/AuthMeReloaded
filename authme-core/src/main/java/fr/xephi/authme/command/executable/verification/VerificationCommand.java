package fr.xephi.authme.command.executable.verification;

import fr.xephi.authme.ConsoleLogger;
import fr.xephi.authme.command.PlayerCommand;
import fr.xephi.authme.data.VerificationCodeManager;
import fr.xephi.authme.output.ConsoleLoggerFactory;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.service.CommonService;
import org.bukkit.entity.Player;

import javax.inject.Inject;
import java.util.List;

/**
 * Used to complete the email verification process.
 */
public class VerificationCommand extends PlayerCommand {

    private final ConsoleLogger logger = ConsoleLoggerFactory.get(VerificationCommand.class);

    @Inject
    private CommonService commonService;

    @Inject
    private VerificationCodeManager codeManager;

    @Override
    public void runCommand(Player player, List<String> arguments) {
        final String playerName = player.getName();

        if (!codeManager.canSendMail()) {
            logger.warning("Mail API is not set");
            commonService.send(player, MessageKey.INCOMPLETE_EMAIL_SETTINGS);
            return;
        }

        if (codeManager.isVerificationRequired(player)) {
            if (!codeManager.isCodeRequired(playerName)) {
                commonService.send(player, MessageKey.VERIFICATION_CODE_EXPIRED);
                return;
            }
            commonService.send(player, codeManager.checkCode(playerName, arguments.get(0))
                ? MessageKey.VERIFICATION_CODE_VERIFIED : MessageKey.INCORRECT_VERIFICATION_CODE);
            return;
        }

        if (codeManager.hasEmail(playerName)) {
            commonService.send(player, MessageKey.VERIFICATION_CODE_ALREADY_VERIFIED);
            return;
        }
        commonService.send(player, MessageKey.VERIFICATION_CODE_EMAIL_NEEDED);
        commonService.send(player, MessageKey.ADD_EMAIL_MESSAGE);
    }

    @Override
    public MessageKey getArgumentsMismatchMessage() {
        return MessageKey.USAGE_VERIFICATION_CODE;
    }
}
