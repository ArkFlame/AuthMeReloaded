package fr.xephi.authme.process.email;

import fr.xephi.authme.ConsoleLogger;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.data.auth.PlayerCache;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.events.EmailChangedEvent;
import fr.xephi.authme.mail.EmailService;
import fr.xephi.authme.output.ConsoleLoggerFactory;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.process.AsynchronousProcess;
import fr.xephi.authme.service.BukkitService;
import fr.xephi.authme.service.CommonService;
import fr.xephi.authme.service.PendingEmailVerificationCache;
import fr.xephi.authme.service.ValidationService;
import org.bukkit.entity.Player;

import javax.inject.Inject;
import java.util.Locale;

/**
 * Async task for changing the email.
 */
public class AsyncChangeEmail implements AsynchronousProcess {

    private final ConsoleLogger logger = ConsoleLoggerFactory.get(AsyncChangeEmail.class);

    @Inject
    private CommonService service;

    @Inject
    private PlayerCache playerCache;

    @Inject
    private DataSource dataSource;

    @Inject
    private ValidationService validationService;

    @Inject
    private BukkitService bukkitService;

    @Inject
    private EmailService emailService;

    @Inject
    private PendingEmailVerificationCache pendingEmailVerificationCache;

    /**
     * Handles the request to change the player's email address.
     * If email sending is available, the new address is held pending confirmation via
     * {@code /email confirm <code>}. Otherwise it is saved directly.
     *
     * @param player   the player to change the email for
     * @param oldEmail provided old email
     * @param newEmail provided new email
     */
    public void changeEmail(Player player, String oldEmail, String newEmail) {
        String playerName = player.getName().toLowerCase(Locale.ROOT);
        PlayerAuth auth = playerCache.isAuthenticated(playerName) ? playerCache.getAuth(playerName) : null;
        if (auth == null) {
            // Not authenticated, or the cache entry was removed while this async task was queued.
            EmailProcessHelper.sendUnloggedMessage(service, dataSource, player);
            return;
        }
        String currentEmail = auth.getEmail();
        if (currentEmail == null) {
            service.send(player, MessageKey.USAGE_ADD_EMAIL);
        } else if (newEmail == null || !validationService.validateEmail(newEmail)) {
            service.send(player, MessageKey.INVALID_NEW_EMAIL);
        } else if (!oldEmail.equalsIgnoreCase(currentEmail)) {
            service.send(player, MessageKey.INVALID_OLD_EMAIL);
        } else if (!validationService.isEmailFreeForRegistration(newEmail, player)) {
            service.send(player, MessageKey.EMAIL_ALREADY_USED_ERROR);
        } else {
            EmailChangedEvent event = bukkitService.createAndCallEvent(isAsync
                -> new EmailChangedEvent(player, oldEmail, newEmail, isAsync));
            if (event.isCancelled()) {
                logger.info("Could not change email for player '" + player + "' – event was cancelled");
                service.send(player, MessageKey.EMAIL_CHANGE_NOT_ALLOWED);
                return;
            }
            if (emailService.hasAllInformation()) {
                EmailProcessHelper.sendConfirmationCode(service, emailService, pendingEmailVerificationCache,
                    logger, player, newEmail);
            } else if (!EmailProcessHelper.persistEmail(service, dataSource, playerCache, auth, player, newEmail,
                MessageKey.EMAIL_CHANGED_SUCCESS)) {
                logger.warning("Could not change email for player '" + player + "'");
            }
        }
    }

}
