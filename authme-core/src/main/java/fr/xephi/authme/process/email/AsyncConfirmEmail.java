package fr.xephi.authme.process.email;

import fr.xephi.authme.ConsoleLogger;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.data.auth.PlayerCache;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.output.ConsoleLoggerFactory;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.process.AsynchronousProcess;
import fr.xephi.authme.service.CommonService;
import fr.xephi.authme.service.PendingEmailVerificationCache;
import org.bukkit.entity.Player;

import javax.inject.Inject;
import java.util.Locale;

/**
 * Async task that validates an email confirmation code and persists the pending address.
 */
public class AsyncConfirmEmail implements AsynchronousProcess {

    private final ConsoleLogger logger = ConsoleLoggerFactory.get(AsyncConfirmEmail.class);

    @Inject
    private CommonService service;

    @Inject
    private PlayerCache playerCache;

    @Inject
    private DataSource dataSource;

    @Inject
    private PendingEmailVerificationCache pendingEmailVerificationCache;

    AsyncConfirmEmail() {
    }

    /**
     * Validates the supplied code against the pending email confirmation for the player.
     * On success, saves the new address to the database and player cache.
     *
     * @param player the player submitting the code
     * @param code   the confirmation code
     */
    public void confirmEmail(Player player, String code) {
        String playerName = player.getName();
        String authName = playerName.toLowerCase(Locale.ROOT);

        if (!playerCache.isAuthenticated(authName)) {
            EmailProcessHelper.sendUnloggedMessage(service, dataSource, player);
            return;
        }

        PendingEmailVerificationCache.PendingEntry entry =
            pendingEmailVerificationCache.getEntry(playerName);
        if (entry == null || !entry.code().equals(code)) {
            service.send(player, entry == null ? MessageKey.EMAIL_CONFIRM_CODE_EXPIRED
                                               : MessageKey.EMAIL_CONFIRM_WRONG_CODE);
            return;
        }

        PlayerAuth auth = playerCache.getAuth(authName);
        if (EmailProcessHelper.persistEmail(service, dataSource, playerCache, auth, player, entry.email(),
                MessageKey.EMAIL_CONFIRM_SUCCESS)) {
            pendingEmailVerificationCache.removePending(playerName);
        } else {
            // Leave the pending entry in place so the code can be retried after a failed save.
            logger.warning("Could not save confirmed email for player '" + player + "'");
        }
    }
}
