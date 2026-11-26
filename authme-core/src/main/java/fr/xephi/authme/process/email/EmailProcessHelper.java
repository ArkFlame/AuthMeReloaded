package fr.xephi.authme.process.email;

import fr.xephi.authme.ConsoleLogger;
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
     * Tells a player who is not logged in to log in or to register, based on his account status.
     */
    static void sendUnloggedMessage(CommonService service, DataSource dataSource, Player player) {
        service.send(player, dataSource.isAuthAvailable(player.getName())
            ? MessageKey.LOGIN_MESSAGE
            : MessageKey.REGISTER_MESSAGE);
    }
}
