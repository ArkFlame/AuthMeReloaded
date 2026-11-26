package fr.xephi.authme.command.executable.totp;

import fr.xephi.authme.ConsoleLogger;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.output.ConsoleLoggerFactory;
import fr.xephi.authme.security.totp.TotpAuthenticator;
import org.bukkit.entity.Player;

import javax.inject.Inject;
import java.util.List;

/**
 * Command for a player to remove 2FA authentication.
 */
public class RemoveTotpCommand extends AbstractTotpPlayerCommand {

    private final ConsoleLogger logger = ConsoleLoggerFactory.get(RemoveTotpCommand.class);

    @Inject
    private DataSource dataSource;
    @Inject
    private TotpAuthenticator totpAuthenticator;

    @Override
    protected void handlePlayer(Player player, PlayerAuth auth, List<String> arguments) {
        if (auth.getTotpKey() == null) {
            messages.send(player, MessageKey.TWO_FACTOR_NOT_ENABLED_ERROR);
        } else if (totpAuthenticator.checkCode(auth, arguments.get(0))) {
            boolean removed = dataSource.removeTotpKey(auth.getNickname());
            applyTotpKeyUpdate(player, auth, removed, null, MessageKey.TWO_FACTOR_REMOVED_SUCCESS);
            if (removed) {
                logger.info("Player '" + player.getName() + "' removed their TOTP key");
            }
        } else {
            messages.send(player, MessageKey.TWO_FACTOR_INVALID_CODE);
        }
    }
}
