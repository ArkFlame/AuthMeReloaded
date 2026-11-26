package fr.xephi.authme.process.email;

import fr.xephi.authme.ConsoleLogger;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.data.auth.PlayerCache;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.mail.EmailService;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.service.CommonService;
import fr.xephi.authme.service.PendingEmailVerificationCache;
import fr.xephi.authme.util.RandomStringUtils;
import org.bukkit.entity.Player;

/**
 * Steps shared by the email-related asynchronous processes.
 */
final class EmailProcessHelper {

    private EmailProcessHelper() {
    }

    /**
     * Sends a confirmation code to the new address and holds the address pending until it is confirmed.
     */
    static void sendConfirmationCode(CommonService service, EmailService emailService,
                                     PendingEmailVerificationCache pendingCache, ConsoleLogger logger,
                                     Player player, String email) {
        String code = RandomStringUtils.generateNum(6);
        if (emailService.sendEmailConfirmationMail(player.getName(), email, code)) {
            pendingCache.addPending(player.getName(), email, code);
            service.send(player, MessageKey.EMAIL_CONFIRM_CODE_SENT, email);
        } else {
            logger.warning("Could not send confirmation email for player '" + player + "'");
            service.send(player, MessageKey.EMAIL_SEND_FAILURE);
        }
    }

    /**
     * Persists the new address to the database and the player cache and informs the player.
     *
     * @return true if the address was saved successfully
     */
    static boolean persistEmail(CommonService service, DataSource dataSource, PlayerCache playerCache,
                                PlayerAuth auth, Player player, String email, MessageKey successKey) {
        String previousEmail = auth.getEmail();
        auth.setEmail(email);
        if (!dataSource.updateEmail(auth)) {
            // auth usually is the instance held by the player cache, so undo the change that was not persisted.
            auth.setEmail(previousEmail);
            service.send(player, MessageKey.ERROR);
            return false;
        }
        playerCache.updatePlayerIfPresent(auth);
        service.send(player, successKey);
        return true;
    }

    /**
     * Tells a player who is not logged in to log in or to register, based on his account status.
     */
    static void sendUnloggedMessage(CommonService service, DataSource dataSource, Player player) {
        service.send(player, dataSource.isAuthAvailable(player.getName())
            ? MessageKey.LOGIN_MESSAGE
            : MessageKey.REGISTER_MESSAGE);
    }
}
