package fr.xephi.authme.mail;

import fr.xephi.authme.ConsoleLogger;
import fr.xephi.authme.initialization.DataFolder;
import fr.xephi.authme.output.ConsoleLoggerFactory;
import fr.xephi.authme.settings.Settings;
import fr.xephi.authme.settings.properties.EmailSettings;
import fr.xephi.authme.settings.properties.PluginSettings;
import fr.xephi.authme.settings.properties.SecuritySettings;
import fr.xephi.authme.util.FileUtils;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.HtmlEmail;

import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import java.io.File;
import java.io.IOException;

/**
 * Creates emails and sends them.
 */
public class EmailService {

    private final ConsoleLogger logger = ConsoleLoggerFactory.get(EmailService.class);

    private final File dataFolder;
    private final Settings settings;
    private final SendMailSsl sendMailSsl;

    @Inject
    EmailService(@DataFolder File dataFolder, Settings settings, SendMailSsl sendMailSsl) {
        this.dataFolder = dataFolder;
        this.settings = settings;
        this.sendMailSsl = sendMailSsl;
    }

    public boolean hasAllInformation() {
        return sendMailSsl.hasAllInformation();
    }

    /**
     * Sends an email to the user with his new password.
     *
     * @param name the name of the player
     * @param mailAddress the player's email
     * @param newPass the new password
     * @return true if email could be sent, false otherwise
     */
    public boolean sendPasswordMail(String name, String mailAddress, String newPass) {
        if (!hasAllInformation()) {
            logger.warning("Cannot perform email registration: not all email settings are complete");
            return false;
        }

        HtmlEmail email = initializeMail(mailAddress, "Failed to create email with the given settings:");
        if (email == null) {
            return false;
        }

        String mailText = replaceTags(settings.getPasswordEmailMessage(), name, "<generatedpass />", newPass);
        // Generate an image?
        File file = null;
        if (settings.getProperty(EmailSettings.PASSWORD_AS_IMAGE)) {
            try {
                file = generatePasswordImage(name, newPass);
                mailText = embedImageIntoEmailContent(file, email, mailText);
            } catch (IOException | EmailException e) {
                logger.logException(
                    "Unable to send new password as image for email " + mailAddress + ":", e);
            }
        }

        boolean couldSendEmail = sendMailSsl.sendEmail(mailText, email);
        FileUtils.delete(file);
        return couldSendEmail;
    }

    /**
     * Sends an email to the user with the temporary verification code.
     *
     * @param name the name of the player
     * @param mailAddress the player's email
     * @param code the verification code
     * @return true if email could be sent, false otherwise
     */
    public boolean sendVerificationMail(String name, String mailAddress, String code) {
        return sendVerificationCodeMail(
            "Cannot send verification email: not all email settings are complete",
            "Failed to create verification email with the given settings:",
            name, mailAddress, code,
            settings.getProperty(SecuritySettings.VERIFICATION_CODE_EXPIRATION_MINUTES),
            settings.getVerificationEmailMessage());
    }

    /**
     * Sends an email to the new address with a code to confirm the address change.
     *
     * @param name  the name of the player
     * @param email the new email address to confirm
     * @param code  the confirmation code
     * @return true if email could be sent, false otherwise
     */
    public boolean sendEmailConfirmationMail(String name, String email, String code) {
        // TTL matches PendingEmailVerificationCache.TTL_MS (10 minutes)
        return sendVerificationCodeMail(
            "Cannot send email confirmation: not all email settings are complete",
            "Failed to create email confirmation mail with the given settings:",
            name, email, code, 10, settings.getEmailConfirmationMessage());
    }

    /**
     * Sends a mail with a verification code once all email settings are complete.
     *
     * @param warning warning to log when email settings are incomplete
     * @param errorMessage message to log when the mail cannot be initialized
     * @param name the name of the player
     * @param mailAddress the recipient address
     * @param code the verification code
     * @param minutesValid number of minutes the code stays valid
     * @param message the mail template
     * @return true if the mail could be sent, false otherwise
     */
    private boolean sendVerificationCodeMail(String warning, String errorMessage, String name, String mailAddress,
                                             String code, int minutesValid, String message) {
        if (!hasAllInformation()) {
            logger.warning(warning);
            return false;
        }

        HtmlEmail email = initializeMail(mailAddress, errorMessage);
        if (email == null) {
            return false;
        }

        String mailText = replaceTags(message, name,
            "<generatedcode />", code, "<minutesvalid />", String.valueOf(minutesValid));
        return sendMailSsl.sendEmail(mailText, email);
    }

    /**
     * Sends an email to the user with a recovery code for the password recovery process.
     *
     * @param name the name of the player
     * @param email the player's email address
     * @param code the recovery code
     * @return true if email could be sent, false otherwise
     */
    public boolean sendRecoveryCode(String name, String email, String code) {
        HtmlEmail htmlEmail = initializeMail(email, "Failed to create email for recovery code:");
        if (htmlEmail == null) {
            return false;
        }

        String message = replaceTags(settings.getRecoveryCodeEmailMessage(), name,
            "<recoverycode />", code,
            "<hoursvalid />", String.valueOf(settings.getProperty(SecuritySettings.RECOVERY_CODE_HOURS_VALID)));
        return sendMailSsl.sendEmail(message, htmlEmail);
    }

    /**
     * Creates a mail object, logging the given message and returning null if the settings are invalid.
     */
    private HtmlEmail initializeMail(String mailAddress, String errorMessage) {
        try {
            return sendMailSsl.initializeMail(mailAddress);
        } catch (EmailException e) {
            logger.logException(errorMessage, e);
            return null;
        }
    }

    private File generatePasswordImage(String name, String newPass) throws IOException {
        ImageGenerator gen = new ImageGenerator(newPass);
        File file = new File(dataFolder, name + "_new_pass.jpg");
        ImageIO.write(gen.generateImage(), "jpg", file);
        return file;
    }

    private static String embedImageIntoEmailContent(File image, HtmlEmail email, String content)
        throws EmailException {
        DataSource source = new FileDataSource(image);
        String tag = email.embed(source, image.getName());
        return content.replace("<image />", "<img src=\"cid:" + tag + "\">");
    }

    private String replaceCommonTags(String mailText, String name) {
        return mailText
            .replace("<playername />", name)
            .replace("<servername />", settings.getProperty(PluginSettings.SERVER_NAME));
    }

    /**
     * Replaces the common tags and then each tag/value pair (in the given order).
     */
    private String replaceTags(String mailText, String name, String... tagValuePairs) {
        String text = replaceCommonTags(mailText, name);
        for (int i = 0; i < tagValuePairs.length; i += 2) {
            text = text.replace(tagValuePairs[i], tagValuePairs[i + 1]);
        }
        return text;
    }
}
