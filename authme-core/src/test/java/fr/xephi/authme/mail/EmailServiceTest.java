package fr.xephi.authme.mail;

import fr.xephi.authme.TestHelper;
import fr.xephi.authme.settings.Settings;
import fr.xephi.authme.settings.properties.EmailSettings;
import fr.xephi.authme.settings.properties.PluginSettings;
import fr.xephi.authme.settings.properties.SecuritySettings;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.HtmlEmail;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Test for {@link EmailService}.
 */
@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    private EmailService emailService;

    @Mock
    private Settings settings;
    @Mock
    private SendMailSsl sendMailSsl;
    @TempDir
    File dataFolder;

    @BeforeAll
    static void initLogger() {
        TestHelper.setupLogger();
    }

    @BeforeEach
    void initFieldsAndService() {
        emailService = new EmailService(dataFolder, settings, sendMailSsl);
    }

    @Test
    void shouldHaveAllInformation() {
        // given
        given(sendMailSsl.hasAllInformation()).willReturn(true);

        // when / then
        assertThat(emailService.hasAllInformation(), equalTo(true));
    }

    @ParameterizedTest
    @MethodSource("passwordMailCases")
    void shouldSendPasswordMail(String name, String newPass, String messageTemplate,
                                boolean sendResult, boolean expectedResult, String expectedMessage)
        throws EmailException {
        // given
        given(settings.getPasswordEmailMessage()).willReturn(messageTemplate);
        given(settings.getProperty(EmailSettings.PASSWORD_AS_IMAGE)).willReturn(false);
        given(settings.getProperty(PluginSettings.SERVER_NAME)).willReturn("serverName");
        HtmlEmail email = mock(HtmlEmail.class);
        given(sendMailSsl.hasAllInformation()).willReturn(true);
        given(sendMailSsl.initializeMail(anyString())).willReturn(email);
        given(sendMailSsl.sendEmail(anyString(), eq(email))).willReturn(sendResult);

        // when
        boolean result = emailService.sendPasswordMail(name, "user@example.com", newPass);

        // then
        assertThat(result, equalTo(expectedResult));
        verify(sendMailSsl).initializeMail("user@example.com");
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(sendMailSsl).sendEmail(messageCaptor.capture(), eq(email));
        assertThat(messageCaptor.getValue(), equalTo(expectedMessage));
    }

    private static Stream<Arguments> passwordMailCases() {
        return Stream.of(
            Arguments.of("Player", "new_password",
                "Hi <playername />, your new password for <servername /> is <generatedpass />",
                true, true, "Hi Player, your new password for serverName is new_password"),
            Arguments.of("bobby", "myPassw0rd",
                "Hi <playername />, your new pass is <generatedpass />",
                false, false, "Hi bobby, your new pass is myPassw0rd")
        );
    }

    @ParameterizedTest
    @MethodSource("creationErrorCases")
    void shouldHandleMailCreationError(boolean passwordMail, String address) throws EmailException {
        // given
        given(sendMailSsl.initializeMail(anyString())).willThrow(EmailException.class);
        if (passwordMail) {
            given(sendMailSsl.hasAllInformation()).willReturn(true);
        }

        // when
        boolean result = passwordMail
            ? emailService.sendPasswordMail("Player", address, "new_password")
            : emailService.sendRecoveryCode("Player", address, "ABC1234");

        // then
        assertThat(result, equalTo(false));
        verify(sendMailSsl).initializeMail(address);
        verify(sendMailSsl, never()).sendEmail(anyString(), any(HtmlEmail.class));
    }

    private static Stream<Arguments> creationErrorCases() {
        return Stream.of(
            Arguments.of(true, "user@example.com"),
            Arguments.of(false, "player@example.org")
        );
    }

    @ParameterizedTest
    @MethodSource("recoveryMailCases")
    void shouldSendRecoveryCode(String name, String address, String code, String serverName,
                                String messageTemplate, boolean sendResult, boolean expectedResult,
                                String expectedMessage) throws EmailException {
        // given
        given(settings.getProperty(SecuritySettings.RECOVERY_CODE_HOURS_VALID)).willReturn(7);
        given(settings.getProperty(PluginSettings.SERVER_NAME)).willReturn(serverName);
        given(settings.getRecoveryCodeEmailMessage()).willReturn(messageTemplate);
        HtmlEmail email = mock(HtmlEmail.class);
        given(sendMailSsl.initializeMail(anyString())).willReturn(email);
        given(sendMailSsl.sendEmail(anyString(), any(HtmlEmail.class))).willReturn(sendResult);

        // when
        boolean result = emailService.sendRecoveryCode(name, address, code);

        // then
        assertThat(result, equalTo(expectedResult));
        verify(sendMailSsl).initializeMail(address);
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(sendMailSsl).sendEmail(messageCaptor.capture(), eq(email));
        assertThat(messageCaptor.getValue(), equalTo(expectedMessage));
    }

    private static Stream<Arguments> recoveryMailCases() {
        return Stream.of(
            Arguments.of("Timmy", "tim@example.com", "12C56A", "serverName",
                "Hi <playername />, your code on <servername /> is <recoverycode /> (valid <hoursvalid /> hours)",
                true, true, "Hi Timmy, your code on serverName is 12C56A (valid 7 hours)"),
            Arguments.of("John", "user@example.com", "1DEF77", "Server? I barely know her!",
                "Hi <playername />, your code is <recoverycode /> for <servername />",
                false, false, "Hi John, your code is 1DEF77 for Server? I barely know her!")
        );
    }
}
