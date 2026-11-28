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

    /**
     * Validates the code against the pending confirmation and, on success, persists the address.
     */
    public void confirmEmail(Player player, String code) {
        String playerName = player.getName();
        String authName = playerName.toLowerCase(Locale.ROOT);

        PlayerAuth auth = playerCache.isAuthenticated(authName) ? playerCache.getAuth(authName) : null;
        if (auth == null) {
            // Not authenticated, or the cache entry was removed while this async task was queued.
            EmailProcessHelper.sendUnloggedMessage(service, dataSource, player);
            return;
        }

        PendingEmailVerificationCache.PendingEntry entry = pendingEmailVerificationCache.getEntry(playerName);
        if (entry == null || !entry.code().equals(code)) {
            if (entry == null) {
                service.send(player, MessageKey.EMAIL_CONFIRM_CODE_EXPIRED);
            } else if (entry.recordWrongAttempt()) {
                service.send(player, MessageKey.EMAIL_CONFIRM_WRONG_CODE);
            } else {
                // Too many wrong codes: discard the request so the code cannot be brute-forced.
                pendingEmailVerificationCache.removePending(playerName);
                service.send(player, MessageKey.EMAIL_CONFIRM_CODE_EXPIRED);
            }
            return;
        }

        if (EmailProcessHelper.persistEmail(service, dataSource, playerCache, auth, player, entry.email(),
                MessageKey.EMAIL_CONFIRM_SUCCESS)) {
            pendingEmailVerificationCache.removePending(playerName);
        } else {
            // Leave the pending entry in place so the code can be retried after a failed save.
            logger.warning("Could not save confirmed email for player '" + playerName + "'");
        }
    }
}
