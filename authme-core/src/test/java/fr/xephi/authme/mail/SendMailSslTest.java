package fr.xephi.authme.mail;

import fr.xephi.authme.TestHelper;
import fr.xephi.authme.output.LogLevel;
import fr.xephi.authme.settings.Settings;
import fr.xephi.authme.settings.properties.EmailSettings;
import fr.xephi.authme.settings.properties.PluginSettings;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.HtmlEmail;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Properties;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.BDDMockito.given;

/**
 * Test for {@link SendMailSsl}.
 */
@ExtendWith(MockitoExtension.class)
class SendMailSslTest {

    @InjectMocks
    private SendMailSsl sendMailSsl;

    @Mock
    private Settings settings;

    @BeforeAll
    static void initLogger() {
        TestHelper.setupLogger();
    }

    @Test
    void shouldHaveAllInformation() {
        given(settings.getProperty(EmailSettings.MAIL_ACCOUNT)).willReturn("mail@example.org");
        given(settings.getProperty(EmailSettings.MAIL_PASSWORD)).willReturn("pass1234");

        assertThat(sendMailSsl.hasAllInformation(), equalTo(true));
    }

    @ParameterizedTest
    @MethodSource("emailObjectCases")
    void shouldCreateEmailObject(String mailAddress, String expectedFrom, LogLevel logLevel) throws EmailException {
        given(settings.getProperty(EmailSettings.SMTP_PORT)).willReturn(465);
        String smtpHost = "mail.example.com";
        given(settings.getProperty(EmailSettings.SMTP_HOST)).willReturn(smtpHost);
        String senderAccount = "sender@example.org";
        given(settings.getProperty(EmailSettings.MAIL_ACCOUNT)).willReturn(senderAccount);
        given(settings.getProperty(EmailSettings.MAIL_ADDRESS)).willReturn(mailAddress);
        given(settings.getProperty(EmailSettings.MAIL_PASSWORD)).willReturn("pass1234");
        String senderName = "Server administration";
        given(settings.getProperty(EmailSettings.MAIL_SENDER_NAME)).willReturn(senderName);
        given(settings.getProperty(EmailSettings.RECOVERY_MAIL_SUBJECT)).willReturn("Recover password");
        given(settings.getProperty(EmailSettings.SSL_CHECK_SERVER_IDENTITY)).willReturn(true);
        given(settings.getProperty(PluginSettings.LOG_LEVEL)).willReturn(logLevel);

        HtmlEmail email = sendMailSsl.initializeMail("recipient@example.com");

        assertThat(email, not(nullValue()));
        assertThat(email.getToAddresses(), hasSize(1));
        assertThat(email.getToAddresses().get(0).getAddress(), equalTo("recipient@example.com"));
        assertThat(email.getFromAddress().getAddress(), equalTo(expectedFrom));
        assertThat(email.getFromAddress().getPersonal(), equalTo(senderName));
        assertThat(email.getHostName(), equalTo(smtpHost));
        assertThat(email.getSmtpPort(), equalTo("465"));
    }

    private static Stream<Arguments> emailObjectCases() {
        return Stream.of(
            Arguments.of("", "sender@example.org", LogLevel.DEBUG),
            Arguments.of("mail@example.com", "mail@example.com", LogLevel.INFO)
        );
    }

    @Test
    void shouldCreateEmailObjectWithOAuth2() throws EmailException {
        given(settings.getProperty(EmailSettings.SMTP_PORT)).willReturn(587);
        given(settings.getProperty(EmailSettings.OAUTH2_TOKEN)).willReturn("oAuth2 token");
        String smtpHost = "mail.example.com";
        given(settings.getProperty(EmailSettings.SMTP_HOST)).willReturn(smtpHost);
        String senderMail = "sender@example.org";
        given(settings.getProperty(EmailSettings.MAIL_ACCOUNT)).willReturn(senderMail);
        given(settings.getProperty(EmailSettings.MAIL_ADDRESS)).willReturn("");
        given(settings.getProperty(EmailSettings.MAIL_PASSWORD)).willReturn("pass1234");
        given(settings.getProperty(EmailSettings.MAIL_SENDER_NAME)).willReturn("Admin");
        given(settings.getProperty(EmailSettings.RECOVERY_MAIL_SUBJECT)).willReturn("Ricóber chur pasword ése");
        given(settings.getProperty(EmailSettings.SSL_CHECK_SERVER_IDENTITY)).willReturn(true);
        given(settings.getProperty(PluginSettings.LOG_LEVEL)).willReturn(LogLevel.INFO);

        HtmlEmail email = sendMailSsl.initializeMail("recipient@example.com");

        assertThat(email, not(nullValue()));
        assertThat(email.getToAddresses(), hasSize(1));
        assertThat(email.getToAddresses().get(0).getAddress(), equalTo("recipient@example.com"));
        assertThat(email.getFromAddress().getAddress(), equalTo(senderMail));
        assertThat(email.getHostName(), equalTo(smtpHost));
        assertThat(email.getSmtpPort(), equalTo("587"));

        Properties mailProperties = email.getMailSession().getProperties();
        assertThat(mailProperties.getProperty("mail.smtp.starttls.enable"), equalTo("true"));
        assertThat(mailProperties.getProperty("mail.smtp.starttls.required"), equalTo("true"));
        assertThat(mailProperties.getProperty("mail.smtp.auth.mechanisms"), equalTo("XOAUTH2"));
        assertThat(mailProperties.getProperty("mail.smtp.auth.plain.disable"), equalTo("true"));
        assertThat(mailProperties.getProperty(OAuth2SaslClientFactory.OAUTH_TOKEN_PROP), equalTo("oAuth2 token"));
    }
}
