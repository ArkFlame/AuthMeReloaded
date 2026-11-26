package fr.xephi.authme.process.register.executors;

import fr.xephi.authme.TestHelper;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.process.SyncProcessManager;
import fr.xephi.authme.process.login.AsynchronousLogin;
import fr.xephi.authme.security.PasswordSecurity;
import fr.xephi.authme.security.crypts.HashedPassword;
import fr.xephi.authme.service.BukkitService;
import fr.xephi.authme.service.CommonService;
import fr.xephi.authme.service.ValidationService;
import fr.xephi.authme.service.ValidationService.ValidationResult;
import fr.xephi.authme.settings.properties.PluginSettings;
import fr.xephi.authme.settings.properties.RegistrationSettings;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static fr.xephi.authme.AuthMeMatchers.equalToHash;
import static fr.xephi.authme.AuthMeMatchers.hasAuthBasicData;
import static fr.xephi.authme.service.BukkitServiceTestHelper.setBukkitServiceToScheduleSyncDelayedTaskWithEntityAndDelay;
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
 * Test for {@link PasswordRegisterExecutor}.
 */
@ExtendWith(MockitoExtension.class)
class PasswordRegisterExecutorTest {

    @InjectMocks
    private PasswordRegisterExecutor executor;

    @Mock
    private ValidationService validationService;
    @Mock
    private CommonService commonService;
    @Mock
    private PasswordSecurity passwordSecurity;
    @Mock
    private BukkitService bukkitService;
    @Mock
    private SyncProcessManager syncProcessManager;
    @Mock
    private AsynchronousLogin asynchronousLogin;

    @ParameterizedTest(name = "valid password={0}")
    @ValueSource(booleans = {true, false})
    void shouldCheckPasswordValidity(boolean valid) {
        String password = "myPass";
        String name = "player040";
        given(validationService.validatePassword(password, name)).willReturn(valid
            ? new ValidationResult()
            : new ValidationResult(MessageKey.PASSWORD_CHARACTERS_ERROR, "[a-z]"));
        Player player = mockPlayerWithName(name);
        PasswordRegisterParams params = PasswordRegisterParams.of(player, password, null);

        assertThat(executor.isRegistrationAdmitted(params), equalTo(valid));
        verify(validationService).validatePassword(password, name);
        if (!valid) {
            verify(commonService).send(player, MessageKey.PASSWORD_CHARACTERS_ERROR, "[a-z]");
        }
    }

    @Test
    void shouldCreatePlayerAuth() {
        given(passwordSecurity.computeHash(anyString(), anyString())).willAnswer(
            invocation -> new HashedPassword(invocation.getArgument(0)));
        Player player = mockPlayerWithName("S1m0N");
        TestHelper.mockIpAddressToPlayer(player, "123.45.67.89");
        PasswordRegisterParams params = PasswordRegisterParams.of(player, "pass", "mail@example.org");

        PlayerAuth auth = executor.buildPlayerAuth(params);

        assertThat(auth, hasAuthBasicData("s1m0n", "S1m0N", "mail@example.org", null));
        assertThat(auth.getRegistrationIp(), equalTo("123.45.67.89"));
        assertThat(Math.abs(auth.getRegistrationDate() - System.currentTimeMillis()), not(greaterThan(500L)));
        assertThat(auth.getPassword(), equalToHash("pass"));
    }

    @ParameterizedTest(name = "force login after register={0}")
    @ValueSource(booleans = {true, false})
    void shouldHandleForceLoginAfterRegistration(boolean forceLogin) {
        given(commonService.getProperty(RegistrationSettings.FORCE_LOGIN_AFTER_REGISTER)).willReturn(forceLogin);
        Player player = mock(Player.class);
        if (!forceLogin) {
            given(commonService.getProperty(PluginSettings.USE_ASYNC_TASKS)).willReturn(false);
            setBukkitServiceToScheduleSyncDelayedTaskWithEntityAndDelay(bukkitService);
        }

        executor.executePostPersistAction(PasswordRegisterParams.of(player, "pass", "mail@example.org"));

        if (forceLogin) {
            verifyNoInteractions(bukkitService, asynchronousLogin);
        } else {
            verify(asynchronousLogin).forceLogin(player);
        }
        verify(syncProcessManager).processSyncPasswordRegister(player);
    }

    private static Player mockPlayerWithName(String name) {
        Player player = mock(Player.class);
        given(player.getName()).willReturn(name);
        return player;
    }
}
