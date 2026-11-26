package fr.xephi.authme.command.executable.totp;

import fr.xephi.authme.ConsoleLogger;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.output.ConsoleLoggerFactory;
import fr.xephi.authme.security.totp.GenerateTotpService;
import fr.xephi.authme.security.totp.TotpAuthenticator.TotpGenerationResult;
import org.bukkit.entity.Player;

import javax.inject.Inject;
import java.util.List;

/**
 * Command to enable TOTP by supplying the proper code as confirmation.
 */
public class ConfirmTotpCommand extends AbstractTotpPlayerCommand {

    private final ConsoleLogger logger = ConsoleLoggerFactory.get(ConfirmTotpCommand.class);

    @Inject
    private GenerateTotpService generateTotpService;

    @Inject
    private DataSource dataSource;

    @Override
    protected void handlePlayer(Player player, PlayerAuth auth, List<String> arguments) {
        if (auth.getTotpKey() != null) {
            messages.send(player, MessageKey.TWO_FACTOR_ALREADY_ENABLED);
        } else {
            verifyTotpCodeConfirmation(player, auth, arguments.get(0));
        }
    }

    private void verifyTotpCodeConfirmation(Player player, PlayerAuth auth, String inputTotpCode) {
        final TotpGenerationResult totpDetails = generateTotpService.getGeneratedTotpKey(player);
        if (totpDetails == null) {
            messages.send(player, MessageKey.TWO_FACTOR_ENABLE_ERROR_NO_CODE);
        } else {
            if (generateTotpService.isTotpCodeCorrectForGeneratedTotpKey(player, inputTotpCode)) {
                insertTotpKeyIntoDatabase(player, auth, totpDetails);
            } else {
                messages.send(player, MessageKey.TWO_FACTOR_ENABLE_ERROR_WRONG_CODE);
            }
        }
    }

    private void insertTotpKeyIntoDatabase(Player player, PlayerAuth auth, TotpGenerationResult totpDetails) {
        String totpKey = totpDetails.getTotpKey();
        String playerName = player.getName();
        boolean updated = dataSource.setTotpKey(playerName, totpKey);
        // Only discard the generated key once it has been persisted, so a failed DB write can be retried
        // without forcing the player to run /totp add (and rescan the QR code) again.
        if (updated) {
            generateTotpService.removeGenerateTotpKey(player);
            logger.info("Player '" + playerName + "' has successfully added a TOTP key to their account");
        }
        applyTotpKeyUpdate(player, auth, updated, totpKey, MessageKey.TWO_FACTOR_ENABLE_SUCCESS);
    }
}
