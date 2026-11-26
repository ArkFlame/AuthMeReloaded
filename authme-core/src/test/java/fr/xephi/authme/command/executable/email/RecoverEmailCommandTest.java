package fr.xephi.authme.command.executable.email;

import ch.jalu.datasourcecolumns.data.DataSourceValueImpl;
import fr.xephi.authme.TestHelper;
import fr.xephi.authme.data.auth.PlayerCache;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.mail.EmailService;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.service.BukkitService;
import fr.xephi.authme.service.CommonService;
import fr.xephi.authme.service.EmailRecoveryDialogService;
import fr.xephi.authme.service.PasswordRecoveryService;
import fr.xephi.authme.service.RecoveryCodeService;
import fr.xephi.authme.service.ValidationService;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.stream.Stream;

import static fr.xephi.authme.service.BukkitServiceTestHelper.setBukkitServiceToRunTaskAsynchronously;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Test for {@link RecoverEmailCommand}.
 */
@ExtendWith(MockitoExtension.class)
class RecoverEmailCommandTest {

    private static final String DEFAULT_EMAIL = "your@email.com";

    @InjectMocks
    private RecoverEmailCommand command;

    @Mock
    private CommonService commonService;
    @Mock
    private DataSource dataSource;
    @Mock
    private PlayerCache playerCache;
    @Mock
    private EmailService emailService;
    @Mock
    private PasswordRecoveryService recoveryService;
    @Mock
    private RecoveryCodeService recoveryCodeService;
    @Mock
    private BukkitService bukkitService;
    @Mock
    private ValidationService validationService;
    @Mock
    private EmailRecoveryDialogService dialogRecoveryService;

    @BeforeAll
    static void initLogger() {
        TestHelper.setupLogger();
    }

    @Test
    void shouldHandleMissingMailProperties() {
        // given
        given(emailService.hasAllInformation()).willReturn(false);
        Player sender = mock(Player.class);

        // when
        command.executeCommand(sender, Collections.singletonList("some@email.tld"));

        // then
        verify(commonService).send(sender, MessageKey.INCOMPLETE_EMAIL_SETTINGS);
        verifyNoInteractions(dataSource);
    }

    @Test
    void shouldShowErrorForAuthenticatedUser() {
        // given
        String name = "Bobby";
        Player sender = mock(Player.class);
        given(sender.getName()).willReturn(name);
        given(emailService.hasAllInformation()).willReturn(true);
        given(playerCache.isAuthenticated(name)).willReturn(true);

        // when
        command.executeCommand(sender, Collections.singletonList("bobby@example.org"));

        // then
        verify(emailService).hasAllInformation();
        verifyNoInteractions(dataSource);
        verify(commonService).send(sender, MessageKey.ALREADY_LOGGED_IN_ERROR);
    }

    @Test
    void shouldRejectInvalidEmailFormat() {
        // given
        Player sender = initSender("SomePlayer");
        given(validationService.validateEmail("notanemail")).willReturn(false);

        // when
        command.executeCommand(sender, Collections.singletonList("notanemail"));

        // then
        verifyNoInteractions(dataSource);
        verify(commonService).send(sender, MessageKey.INVALID_EMAIL);
    }

    @Test
    void shouldShowRegisterMessageForUnregisteredPlayer() {
        // given
        String name = "Player123";
        Player sender = initSender(name);
        given(validationService.validateEmail("someone@example.com")).willReturn(true);
        given(dataSource.getEmail(name)).willReturn(DataSourceValueImpl.unknownRow());

        // when
        command.executeCommand(sender, Collections.singletonList("someone@example.com"));

        // then
        verifyNoMoreInteractions(dataSource);
        verify(commonService).send(sender, MessageKey.USAGE_REGISTER);
    }

    @ParameterizedTest(name = "requested {0}, stored {1}")
    @MethodSource("invalidEmailCases")
    void shouldRejectInvalidEmail(String requestedEmail, String storedEmail) {
        // given
        String name = "Tract0r";
        Player sender = initSender(name);
        given(validationService.validateEmail(requestedEmail)).willReturn(true);
        given(dataSource.getEmail(name)).willReturn(DataSourceValueImpl.of(storedEmail));

        // when
        command.executeCommand(sender, Collections.singletonList(requestedEmail));

        // then
        verifyNoMoreInteractions(dataSource);
        verify(commonService).send(sender, MessageKey.INVALID_EMAIL);
    }

    private static Stream<Arguments> invalidEmailCases() {
        return Stream.of(
            Arguments.of(DEFAULT_EMAIL, DEFAULT_EMAIL),
            Arguments.of("wrong-email@example.com", "raptor@example.org"));
    }

    @ParameterizedTest(name = "stored {0}, requested {1}, recovery code {2}")
    @MethodSource("recoveryCases")
    void shouldSendRecoveryMail(String email, String requestedEmail, boolean recoveryCodeNeeded) {
        // given
        String name = "Vultur3";
        Player sender = initSender(name);
        given(validationService.validateEmail(requestedEmail)).willReturn(true);
        given(dataSource.getEmail(name)).willReturn(DataSourceValueImpl.of(email));
        given(recoveryCodeService.isRecoveryCodeNeeded()).willReturn(recoveryCodeNeeded);
        setBukkitServiceToRunTaskAsynchronously(bukkitService);

        // when
        command.executeCommand(sender, Collections.singletonList(requestedEmail));

        // then
        if (recoveryCodeNeeded) {
            verify(recoveryService, only()).createAndSendRecoveryCode(sender, email);
        } else {
            verify(recoveryService, only()).generateAndSendNewPassword(sender, email);
        }
    }

    private static Stream<Arguments> recoveryCases() {
        return Stream.of(
            Arguments.of("v@example.com", "V@EXAMPLE.COM", true),
            Arguments.of("vulture@example.com", "vulture@example.com", false));
    }

    @Test
    void shouldDefineArgumentMismatchMessage() {
        // given / when / then
        assertThat(command.getArgumentsMismatchMessage(), equalTo(MessageKey.USAGE_RECOVER_EMAIL));
    }

    private Player initSender(String name) {
        Player sender = mock(Player.class);
        given(sender.getName()).willReturn(name);
        given(emailService.hasAllInformation()).willReturn(true);
        given(playerCache.isAuthenticated(name)).willReturn(false);
        return sender;
    }
}
