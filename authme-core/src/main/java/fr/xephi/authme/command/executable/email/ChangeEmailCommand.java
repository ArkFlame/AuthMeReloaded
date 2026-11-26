package fr.xephi.authme.command.executable.email;

import fr.xephi.authme.data.VerificationCodeManager;
import fr.xephi.authme.message.MessageKey;
import org.bukkit.entity.Player;

import javax.inject.Inject;
import java.util.List;

/**
 * Change email command.
 */
public class ChangeEmailCommand extends AbstractEmailCommand {

    @Inject
    private VerificationCodeManager codeManager;

    @Override
    public void runCommand(Player player, List<String> arguments) {
        final String playerName = player.getName();
        if (codeManager.isVerificationRequired(player)) {
            codeManager.codeExistOrGenerateNew(playerName);
            getCommonService().send(player, MessageKey.VERIFICATION_CODE_REQUIRED);
            return;
        }

        String playerMailOld = arguments.get(0);
        String playerMailNew = arguments.get(1);
        if (!isEmailValid(player, playerMailOld, MessageKey.INVALID_OLD_EMAIL)) {
            return;
        }
        if (!isEmailValid(player, playerMailNew, MessageKey.INVALID_NEW_EMAIL)) {
            return;
        }
        getManagement().performChangeEmail(player, playerMailOld, playerMailNew);
    }

    @Override
    public MessageKey getArgumentsMismatchMessage() {
        return MessageKey.USAGE_CHANGE_EMAIL;
    }
}
