package fr.xephi.authme.command.executable.totp;

import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.security.totp.GenerateTotpService;
import fr.xephi.authme.security.totp.TotpAuthenticator.TotpGenerationResult;
import org.bukkit.entity.Player;

import javax.inject.Inject;
import java.util.List;

/**
 * Command for a player to enable TOTP.
 */
public class AddTotpCommand extends AbstractTotpPlayerCommand {

    @Inject
    private GenerateTotpService generateTotpService;

    @Override
    protected void handlePlayer(Player player, PlayerAuth auth, List<String> arguments) {
        if (auth.getTotpKey() != null) {
            messages.send(player, MessageKey.TWO_FACTOR_ALREADY_ENABLED);
            return;
        }
        TotpGenerationResult totp = generateTotpService.generateTotpKey(player);
        messages.send(player, MessageKey.TWO_FACTOR_CREATE, totp.getTotpKey(), totp.getAuthenticatorQrCodeUrl());
        messages.send(player, MessageKey.TWO_FACTOR_CREATE_CONFIRMATION_REQUIRED);
    }
}
