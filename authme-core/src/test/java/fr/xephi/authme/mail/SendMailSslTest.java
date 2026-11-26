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
        stubEmailSettings(465, mailAddress, "Server administration", "Recover password", logLevel);

        HtmlEmail email = sendMailSsl.initializeMail("recipient@example.com");

        assertThat(email, not(nullValue()));
        assertThat(email.getToAddresses(), hasSize(1));
        assertThat(email.getToAddresses().get(0).getAddress(), equalTo("recipient@example.com"));
        assertThat(email.getFromAddress().getAddress(), equalTo(expectedFrom));
        assertThat(email.getFromAddress().getPersonal(), equalTo("Server administration"));
        assertThat(email.getHostName(), equalTo("mail.example.com"));
        assertThat(email.getSmtpPort(), equalTo("465"));
    }

    private static Stream<Arguments> emailObjectCases() {
        return Stream.of(
            Arguments.of("", "sender@example.org", LogLevel.DEBUG),
            Arguments.of("mail@example.com", "mail@example.com", LogLevel.INFO)
        );
    }

    private void stubEmailSettings(int smtpPort, String mailAddress, String senderName,
                                   String subject, LogLevel logLevel) {
        given(settings.getProperty(EmailSettings.SMTP_PORT)).willReturn(smtpPort);
        given(settings.getProperty(EmailSettings.SMTP_HOST)).willReturn("mail.example.com");
        given(settings.getProperty(EmailSettings.MAIL_ACCOUNT)).willReturn("sender@example.org");
        given(settings.getProperty(EmailSettings.MAIL_ADDRESS)).willReturn(mailAddress);
        given(settings.getProperty(EmailSettings.MAIL_PASSWORD)).willReturn("pass1234");
        given(settings.getProperty(EmailSettings.MAIL_SENDER_NAME)).willReturn(senderName);
        given(settings.getProperty(EmailSettings.RECOVERY_MAIL_SUBJECT)).willReturn(subject);
        given(settings.getProperty(EmailSettings.SSL_CHECK_SERVER_IDENTITY)).willReturn(true);
        given(settings.getProperty(PluginSettings.LOG_LEVEL)).willReturn(logLevel);
    }

    @Test
    void shouldCreateEmailObjectWithOAuth2() throws EmailException {
        stubEmailSettings(587, "", "Admin", "Ricóber chur pasword ése", LogLevel.INFO);
        given(settings.getProperty(EmailSettings.OAUTH2_TOKEN)).willReturn("oAuth2 token");

        HtmlEmail email = sendMailSsl.initializeMail("recipient@example.com");

        assertThat(email, not(nullValue()));
        assertThat(email.getToAddresses(), hasSize(1));
        assertThat(email.getToAddresses().get(0).getAddress(), equalTo("recipient@example.com"));
        assertThat(email.getFromAddress().getAddress(), equalTo("sender@example.org"));
        assertThat(email.getHostName(), equalTo("mail.example.com"));
        assertThat(email.getSmtpPort(), equalTo("587"));

        Properties mailProperties = email.getMailSession().getProperties();
        assertThat(mailProperties.getProperty("mail.smtp.starttls.enable"), equalTo("true"));
        assertThat(mailProperties.getProperty("mail.smtp.starttls.required"), equalTo("true"));
        assertThat(mailProperties.getProperty("mail.smtp.auth.mechanisms"), equalTo("XOAUTH2"));
        assertThat(mailProperties.getProperty("mail.smtp.auth.plain.disable"), equalTo("true"));
        assertThat(mailProperties.getProperty(OAuth2SaslClientFactory.OAUTH_TOKEN_PROP), equalTo("oAuth2 token"));
    }
}
