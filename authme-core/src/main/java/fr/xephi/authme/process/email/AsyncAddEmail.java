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
import fr.xephi.authme.settings.properties.RegistrationSettings;
import fr.xephi.authme.util.RandomStringUtils;
import fr.xephi.authme.util.Utils;
import org.bukkit.entity.Player;

import javax.inject.Inject;
import java.util.Locale;

import static fr.xephi.authme.process.register.RegisterSecondaryArgument.EMAIL_OPTIONAL;

/**
 * Async task to add an email to an account.
 */
public class AsyncAddEmail implements AsynchronousProcess {

    private final ConsoleLogger logger = ConsoleLoggerFactory.get(AsyncAddEmail.class);

    @Inject
    private CommonService service;

    @Inject
    private DataSource dataSource;

    @Inject
    private PlayerCache playerCache;

    @Inject
    private ValidationService validationService;

    @Inject
    private BukkitService bukkitService;

    @Inject
    private EmailService emailService;

    @Inject
    private PendingEmailVerificationCache pendingEmailVerificationCache;

    AsyncAddEmail() {
    }

    /**
     * Handles the request to add the given email to the player's account.
     * If email sending is available and email confirmation is required, the address is held pending
     * confirmation via {@code /email confirm <code>}. Otherwise it is saved directly.
     *
     * @param player the player to add the email to
     * @param email the email to add
     */
    public void addEmail(Player player, String email) {
        String playerName = player.getName().toLowerCase(Locale.ROOT);

        if (playerCache.isAuthenticated(playerName)) {
            PlayerAuth auth = playerCache.getAuth(playerName);
            String currentEmail = auth.getEmail();

            if (!Utils.isEmailEmpty(currentEmail)) {
                service.send(player, MessageKey.USAGE_CHANGE_EMAIL);
            } else if (!validationService.validateEmail(email)) {
                service.send(player, MessageKey.INVALID_EMAIL);
            } else if (!validationService.isEmailFreeForRegistration(email, player)) {
                service.send(player, MessageKey.EMAIL_ALREADY_USED_ERROR);
            } else {
                EmailChangedEvent event = bukkitService.createAndCallEvent(isAsync
                    -> new EmailChangedEvent(player, null, email, isAsync));
                if (event.isCancelled()) {
                    logger.info("Could not add email to player '" + player + "' – event was cancelled");
                    service.send(player, MessageKey.EMAIL_ADD_NOT_ALLOWED);
                    return;
                }
                if (requiresEmailConfirmation()) {
                    sendConfirmationCode(player, email);
                } else {
                    saveEmailDirectly(auth, player, email);
                }
            }
        } else {
            sendUnloggedMessage(player);
        }
    }

    /**
     * Returns whether the given email must be confirmed before it is saved. Registration with
     * {@code EMAIL_OPTIONAL} saves the email directly, so adding one later must do the same.
     *
     * @return true if a confirmation code should be sent
     */
    private boolean requiresEmailConfirmation() {
        return emailService.hasAllInformation()
            && service.getProperty(RegistrationSettings.REGISTER_SECOND_ARGUMENT) != EMAIL_OPTIONAL;
    }

    private void sendConfirmationCode(Player player, String email) {
        String code = RandomStringUtils.generateNum(6);
        if (emailService.sendEmailConfirmationMail(player.getName(), email, code)) {
            pendingEmailVerificationCache.addPending(player.getName(), email, code);
            service.send(player, MessageKey.EMAIL_CONFIRM_CODE_SENT, email);
        } else {
            logger.warning("Could not send confirmation email for player '" + player + "'");
            service.send(player, MessageKey.EMAIL_SEND_FAILURE);
        }
    }

    private void saveEmailDirectly(PlayerAuth auth, Player player, String email) {
        auth.setEmail(email);
        if (dataSource.updateEmail(auth)) {
            playerCache.updatePlayerIfPresent(auth);
            service.send(player, MessageKey.EMAIL_ADDED_SUCCESS);
        } else {
            logger.warning("Could not save email for player '" + player + "'");
            service.send(player, MessageKey.ERROR);
        }
    }

    private void sendUnloggedMessage(Player player) {
        if (dataSource.isAuthAvailable(player.getName())) {
            service.send(player, MessageKey.LOGIN_MESSAGE);
        } else {
            service.send(player, MessageKey.REGISTER_MESSAGE);
        }
    }

}
