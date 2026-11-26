package fr.xephi.authme.process.register.executors;

import fr.xephi.authme.TestHelper;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.mail.EmailService;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.permission.PlayerStatePermission;
import fr.xephi.authme.process.SyncProcessManager;
import fr.xephi.authme.security.PasswordSecurity;
import fr.xephi.authme.security.crypts.HashedPassword;
import fr.xephi.authme.service.CommonService;
import fr.xephi.authme.settings.properties.EmailSettings;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static fr.xephi.authme.AuthMeMatchers.hasAuthBasicData;
import static fr.xephi.authme.AuthMeMatchers.stringWithLength;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Test for {@link EmailRegisterExecutor}.
 */
@ExtendWith(MockitoExtension.class)
class EmailRegisterExecutorTest {

    @InjectMocks
    private EmailRegisterExecutor executor;

    @Mock
    private DataSource dataSource;
    @Mock
    private CommonService commonService;
    @Mock
    private EmailService emailService;
    @Mock
    private SyncProcessManager syncProcessManager;
    @Mock
    private PasswordSecurity passwordSecurity;

    @ParameterizedTest(name = "maxReg={0}, otherAccounts={1}, permission={2}, admitted={3}")
    @CsvSource({"3, 4, false, false", "3, 0, true, true", "1, 0, false, true"})
    void shouldCheckRegistrationAdmitted(int maxReg, int otherAccounts, boolean hasMultipleAccountsPermission,
                                         boolean admitted) {
        String email = "test@example.com";
        Player player = mock(Player.class);
        given(commonService.getProperty(EmailSettings.MAX_REG_PER_EMAIL)).willReturn(maxReg);
        if (hasMultipleAccountsPermission) {
            given(commonService.hasPermission(player, PlayerStatePermission.ALLOW_MULTIPLE_ACCOUNTS)).willReturn(true);
        } else {
            given(dataSource.countAuthsByEmail(email)).willReturn(otherAccounts);
        }
        EmailRegisterParams params = EmailRegisterParams.of(player, email);

        boolean result = executor.isRegistrationAdmitted(params);

        assertThat(result, equalTo(admitted));
        verify(commonService).hasPermission(player, PlayerStatePermission.ALLOW_MULTIPLE_ACCOUNTS);
        if (!hasMultipleAccountsPermission) {
            verify(dataSource).countAuthsByEmail(email);
        }
        if (!admitted) {
            verify(commonService).send(player, MessageKey.MAX_REGISTER_EXCEEDED,
                Integer.toString(maxReg), Integer.toString(otherAccounts), "@");
        }
    }

    @Test
    void shouldCreatePlayerAuth() {
        given(commonService.getProperty(EmailSettings.RECOVERY_PASSWORD_LENGTH)).willReturn(12);
        given(passwordSecurity.computeHash(anyString(), anyString())).willAnswer(
            invocation -> new HashedPassword(invocation.getArgument(0)));
        Player player = mock(Player.class);
        TestHelper.mockIpAddressToPlayer(player, "123.45.67.89");
        given(player.getName()).willReturn("Veronica");
        EmailRegisterParams params = EmailRegisterParams.of(player, "test@example.com");

        PlayerAuth auth = executor.buildPlayerAuth(params);

        assertThat(auth, hasAuthBasicData("veronica", "Veronica", "test@example.com", null));
        assertThat(auth.getRegistrationIp(), equalTo("123.45.67.89"));
        assertThat(Math.abs(auth.getRegistrationDate() - System.currentTimeMillis()), not(greaterThan(1000L)));
        assertThat(auth.getPassword().getHash(), stringWithLength(12));
    }

    @ParameterizedTest(name = "mail sent={0}")
    @ValueSource(booleans = {true, false})
    void shouldPerformActionAfterDataSourceSave(boolean mailSent) {
        given(emailService.sendPasswordMail(anyString(), anyString(), anyString())).willReturn(mailSent);
        Player player = mock(Player.class);
        given(player.getName()).willReturn("Laleh");
        EmailRegisterParams params = EmailRegisterParams.of(player, "test@example.com");
        String password = "A892C#@";
        params.setPassword(password);

        executor.executePostPersistAction(params);

        verify(emailService).sendPasswordMail("Laleh", "test@example.com", password);
        if (mailSent) {
            verify(syncProcessManager).processSyncEmailRegister(player);
        } else {
            verify(commonService).send(player, MessageKey.EMAIL_SEND_FAILURE);
            verifyNoInteractions(syncProcessManager);
        }
    }
}
