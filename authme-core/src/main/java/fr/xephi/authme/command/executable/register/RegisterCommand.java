package fr.xephi.authme.command.executable.register;

import fr.xephi.authme.ConsoleLogger;
import fr.xephi.authme.command.PlayerCommand;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.data.captcha.RegistrationCaptchaManager;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.mail.EmailService;
import fr.xephi.authme.output.ConsoleLoggerFactory;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.process.Management;
import fr.xephi.authme.process.register.RegisterSecondaryArgument;
import fr.xephi.authme.process.register.RegistrationType;
import fr.xephi.authme.process.register.executors.EmailRegisterParams;
import fr.xephi.authme.process.register.executors.PasswordRegisterParams;
import fr.xephi.authme.process.register.executors.RegistrationMethod;
import fr.xephi.authme.process.register.executors.TwoFactorRegisterParams;
import fr.xephi.authme.security.HashAlgorithm;
import fr.xephi.authme.service.BukkitService;
import fr.xephi.authme.service.CommonService;
import fr.xephi.authme.service.ValidationService;
import fr.xephi.authme.settings.properties.EmailSettings;
import fr.xephi.authme.settings.properties.RegistrationSettings;
import fr.xephi.authme.settings.properties.SecuritySettings;
import org.bukkit.entity.Player;

import javax.inject.Inject;
import java.util.List;

import static fr.xephi.authme.process.register.RegisterSecondaryArgument.CONFIRMATION;
import static fr.xephi.authme.process.register.RegisterSecondaryArgument.EMAIL_MANDATORY;
import static fr.xephi.authme.process.register.RegisterSecondaryArgument.EMAIL_OPTIONAL;
import static fr.xephi.authme.process.register.RegisterSecondaryArgument.NONE;
import static fr.xephi.authme.settings.properties.RegistrationSettings.REGISTER_SECOND_ARGUMENT;

/**
 * Command for /register.
 */
public class RegisterCommand extends PlayerCommand {

    private final ConsoleLogger logger = ConsoleLoggerFactory.get(RegisterCommand.class);

    @Inject
    private Management management;

    @Inject
    private CommonService commonService;

    @Inject
    private BukkitService bukkitService;

    @Inject
    private DataSource dataSource;

    @Inject
    private EmailService emailService;

    @Inject
    private ValidationService validationService;

    @Inject
    private RegistrationCaptchaManager registrationCaptchaManager;

    @Override
    public void runCommand(Player player, List<String> arguments) {
        if (!isCaptchaFulfilled(player)) {
            return; // isCaptchaFulfilled handles informing the player on failure
        }

        if (commonService.getProperty(SecuritySettings.PASSWORD_HASH) == HashAlgorithm.TWO_FACTOR) {
            //for two factor auth we don't need to check the usage
            management.performRegister(RegistrationMethod.TWO_FACTOR_REGISTRATION,
                TwoFactorRegisterParams.of(player));
            return;
        }
        if (arguments.isEmpty()) {
            commonService.send(player, MessageKey.USAGE_REGISTER);
            return;
        }

        RegistrationType registrationType = commonService.getProperty(RegistrationSettings.REGISTRATION_TYPE);
        if (registrationType == RegistrationType.PASSWORD) {
            handlePasswordRegistration(player, arguments);
        } else if (registrationType == RegistrationType.EMAIL) {
            handleEmailRegistration(player, arguments);
        } else {
            throw new IllegalStateException("Unknown registration type '" + registrationType + "'");
        }
    }

    @Override
    protected String getAlternativeCommand() {
        return "/authme register <playername> <password>";
    }

    @Override
    public MessageKey getArgumentsMismatchMessage() {
        return MessageKey.USAGE_REGISTER;
    }

    private boolean isCaptchaFulfilled(Player player) {
        String playerName = player.getName();
        if (registrationCaptchaManager.isCaptchaRequired(playerName)) {
            String code = registrationCaptchaManager.getCaptchaCodeOrGenerateNew(playerName);
            commonService.send(player, MessageKey.CAPTCHA_FOR_REGISTRATION_REQUIRED, code);
            return false;
        }
        return true;
    }

    private void handlePasswordRegistration(Player player, List<String> arguments) {
        RegisterSecondaryArgument secondArgType = commonService.getProperty(REGISTER_SECOND_ARGUMENT);
        if (isSecondArgValid(player, arguments, false, secondArgType)) {
            String email = arguments.size() >= 2
                && (secondArgType == EMAIL_MANDATORY || secondArgType == EMAIL_OPTIONAL)
                ? arguments.get(1) : null;
            management.performRegister(RegistrationMethod.PASSWORD_REGISTRATION,
                PasswordRegisterParams.of(player, arguments.get(0), email));
        }
    }

    /**
     * Verifies the second argument for either registration flow and informs the player on failure.
     */
    private boolean isSecondArgValid(Player player, List<String> arguments, boolean emailRegistration,
                                     RegisterSecondaryArgument secondArgType) {
        // No second argument needed if the type is disabled or optional and absent
        if (secondArgType == NONE || secondArgType == EMAIL_OPTIONAL && arguments.size() < 2) {
            return true;
        }
        if (arguments.size() < 2) {
            commonService.send(player, MessageKey.USAGE_REGISTER);
            return false;
        }

        if (emailRegistration || secondArgType == CONFIRMATION) {
            if (arguments.get(0).equals(arguments.get(1))) {
                return true;
            }
            commonService.send(player, emailRegistration ? MessageKey.USAGE_REGISTER : MessageKey.PASSWORD_MATCH_ERROR);
            return false;
        } else if (secondArgType == EMAIL_MANDATORY || secondArgType == EMAIL_OPTIONAL) {
            if (validationService.validateEmail(arguments.get(1))) {
                return true;
            }
            commonService.send(player, MessageKey.INVALID_EMAIL);
            return false;
        }
        throw new IllegalStateException("Unknown secondary argument type '" + secondArgType + "'");
    }

    private void handleEmailRegistration(Player player, List<String> arguments) {
        if (!emailService.hasAllInformation()) {
            commonService.send(player, MessageKey.INCOMPLETE_EMAIL_SETTINGS);
            logger.warning("Cannot register player '" + player.getName() + "': no email or password is set "
                + "to send emails from. Please adjust your config at " + EmailSettings.MAIL_ACCOUNT.getPath());
            return;
        }

        final String email = arguments.get(0);
        if (!validationService.validateEmail(email)) {
            commonService.send(player, MessageKey.INVALID_EMAIL);
        } else if (isSecondArgValid(player, arguments, true,
            commonService.getProperty(REGISTER_SECOND_ARGUMENT))) {
            management.performRegister(RegistrationMethod.EMAIL_REGISTRATION,
                EmailRegisterParams.of(player, email));
            if (commonService.getProperty(RegistrationSettings.UNREGISTER_ON_EMAIL_VERIFICATION_FAILURE)) {
                long unregisterAfterMinutes =
                    commonService.getProperty(RegistrationSettings.UNREGISTER_AFTER_MINUTES);
                if (unregisterAfterMinutes > 0) {
                    bukkitService.runTaskLater(player, () -> {
                        String playerName = player.getName();
                        PlayerAuth auth = dataSource.getAuth(playerName);
                        if (auth != null && auth.getLastLogin() == null) {
                            management.performUnregisterByAdmin(null, playerName, player);
                        }
                    }, 60L * 20L * unregisterAfterMinutes);
                }
            }
        }
    }
}
