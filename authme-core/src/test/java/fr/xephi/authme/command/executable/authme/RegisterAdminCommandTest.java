package fr.xephi.authme.command.executable.authme;

import fr.xephi.authme.TestHelper;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.security.PasswordSecurity;
import fr.xephi.authme.security.crypts.HashedPassword;
import fr.xephi.authme.service.BukkitService;
import fr.xephi.authme.service.CommonService;
import fr.xephi.authme.service.ValidationService;
import fr.xephi.authme.service.ValidationService.ValidationResult;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Locale;

import static fr.xephi.authme.service.BukkitServiceTestHelper.setBukkitServiceToRunTaskOptionallyAsync;
import static fr.xephi.authme.service.BukkitServiceTestHelper.setBukkitServiceToScheduleSyncEntityTaskFromOptionallyAsyncTask;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Test for {@link RegisterAdminCommand}.
 */
@ExtendWith(MockitoExtension.class)
class RegisterAdminCommandTest {

    @InjectMocks
    private RegisterAdminCommand command;

    @Mock
    private PasswordSecurity passwordSecurity;
    @Mock
    private DataSource dataSource;
    @Mock
    private BukkitService bukkitService;
    @Mock
    private CommonService commandService;
    @Mock
    private ValidationService validationService;
    @Mock
    private CommandSender sender;

    @BeforeAll
    static void setUpLogger() {
        TestHelper.setupLogger();
    }

    @Test
    void shouldRejectInvalidPassword() {
        // given
        String user = "tester";
        String password = "myPassword";
        given(validationService.validatePassword(password, user))
            .willReturn(new ValidationResult(MessageKey.INVALID_PASSWORD_LENGTH));

        // when
        command.executeCommand(sender, Arrays.asList(user, password));

        // then
        verify(validationService).validatePassword(password, user);
        verify(commandService).send(sender, MessageKey.INVALID_PASSWORD_LENGTH, new String[0]);
        verify(bukkitService, never()).runTaskAsynchronously(any(Runnable.class));
    }

    @Test
    void shouldRejectAlreadyRegisteredAccount() {
        // given
        String user = "my_name55";
        String password = "@some-pass@";
        given(validationService.validatePassword(password, user)).willReturn(new ValidationResult());
        given(dataSource.isAuthAvailable(user)).willReturn(true);
        setBukkitServiceToRunTaskOptionallyAsync(bukkitService);

        // when
        command.executeCommand(sender, Arrays.asList(user, password));

        // then
        verify(validationService).validatePassword(password, user);
        verify(commandService).send(sender, MessageKey.NAME_ALREADY_REGISTERED);
        verify(dataSource, never()).saveAuth(any(PlayerAuth.class));
    }

    @Test
    void shouldHandleSavingError() {
        // given
        String user = "test-test";
        String password = "afdjhfkt";
        HashedPassword hashedPassword = givenValidRegistration(user, password, "235sdf4w5udsgf", false);
        setBukkitServiceToRunTaskOptionallyAsync(bukkitService);

        // when
        command.executeCommand(sender, Arrays.asList(user, password));

        // then
        verify(validationService).validatePassword(password, user);
        verify(commandService).send(sender, MessageKey.ERROR);
        verifyAuthSaved(user, hashedPassword);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldRegisterPlayer(boolean playerOnline) {
        // given
        String user = "someone";
        String password = "Al1O3P49S5%";
        String kickForAdminRegister = "Admin registered you -- log in again";
        HashedPassword hashedPassword = givenValidRegistration(user, password, "$aea2345EW235dfsa@#R%987048", true);
        Player player = playerOnline ? mock(Player.class) : null;
        given(bukkitService.getPlayerExact(user)).willReturn(player);
        if (player != null) {
            given(commandService.retrieveSingleMessage(player, MessageKey.KICK_FOR_ADMIN_REGISTER)).willReturn(kickForAdminRegister);
            setBukkitServiceToScheduleSyncEntityTaskFromOptionallyAsyncTask(bukkitService);
        }
        setBukkitServiceToRunTaskOptionallyAsync(bukkitService);

        // when
        command.executeCommand(sender, Arrays.asList(user, password));

        // then
        verify(validationService).validatePassword(password, user);
        verify(commandService).send(sender, MessageKey.REGISTER_SUCCESS);
        verifyAuthSaved(user, hashedPassword);
        if (playerOnline) {
            verify(player).kickPlayer(kickForAdminRegister);
        }
    }

    private HashedPassword givenValidRegistration(String user, String password, String hash, boolean saveSuccessful) {
        given(validationService.validatePassword(password, user)).willReturn(new ValidationResult());
        given(dataSource.isAuthAvailable(user)).willReturn(false);
        given(dataSource.saveAuth(any(PlayerAuth.class))).willReturn(saveSuccessful);
        HashedPassword hashedPassword = new HashedPassword(hash);
        given(passwordSecurity.computeHash(password, user)).willReturn(hashedPassword);
        return hashedPassword;
    }

    private void verifyAuthSaved(String user, HashedPassword hashedPassword) {
        ArgumentCaptor<PlayerAuth> captor = ArgumentCaptor.forClass(PlayerAuth.class);
        verify(dataSource).saveAuth(captor.capture());
        PlayerAuth auth = captor.getValue();
        assertThat(auth.getRealName(), equalTo(user));
        assertThat(auth.getNickname(), equalTo(user.toLowerCase(Locale.ROOT)));
        assertThat(auth.getPassword(), equalTo(hashedPassword));
    }
}
