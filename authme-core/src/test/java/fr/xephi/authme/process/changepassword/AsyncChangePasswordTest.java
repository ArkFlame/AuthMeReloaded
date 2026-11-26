package fr.xephi.authme.process.changepassword;

import fr.xephi.authme.TestHelper;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.data.auth.PlayerCache;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.security.PasswordSecurity;
import fr.xephi.authme.security.crypts.HashedPassword;
import fr.xephi.authme.service.CommonService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Test for {@link AsyncChangePassword}.
 */
@ExtendWith(MockitoExtension.class)
class AsyncChangePasswordTest {

    @InjectMocks
    private AsyncChangePassword asyncChangePassword;

    @Mock
    private CommonService commonService;
    @Mock
    private DataSource dataSource;
    @Mock
    private PlayerCache playerCache;
    @Mock
    private PasswordSecurity passwordSecurity;

    @BeforeEach
    void setUpLogger() {
        TestHelper.setupLogger();
    }

    @Test
    void shouldRejectCommandForUnknownUser() {
        CommandSender sender = mock(CommandSender.class);
        String player = "player";
        String password = "password";
        given(playerCache.isAuthenticated(player)).willReturn(false);
        given(dataSource.isAuthAvailable(player)).willReturn(false);

        asyncChangePassword.changePasswordAsAdmin(sender, player, password);

        verify(commonService).send(sender, MessageKey.UNKNOWN_USER);
        verify(dataSource, only()).isAuthAvailable(player);
    }

    @Test
    void shouldDoNothingIfPlayerIsNoLongerLoggedIn() {
        Player player = mock(Player.class);
        given(player.getName()).willReturn("Bobby");
        given(playerCache.getAuth("bobby")).willReturn(null);

        asyncChangePassword.changePassword(player, "old", "new");

        verifyNoInteractions(passwordSecurity, dataSource, commonService);
    }

    @Test
    void shouldNotMutateCachedPasswordWhenDatabaseSaveFails() {
        Player player = mock(Player.class);
        given(player.getName()).willReturn("Bobby");
        PlayerAuth auth = mock(PlayerAuth.class);
        given(playerCache.getAuth("bobby")).willReturn(auth);
        given(passwordSecurity.comparePassword("old", null, "Bobby")).willReturn(true);
        HashedPassword hashedPassword = mock(HashedPassword.class);
        given(passwordSecurity.computeHash("new", "bobby")).willReturn(hashedPassword);
        given(dataSource.updatePassword("bobby", hashedPassword)).willReturn(false);

        asyncChangePassword.changePassword(player, "old", "new");

        verify(dataSource).updatePassword("bobby", hashedPassword);
        verify(auth, never()).setPassword(any(HashedPassword.class));
        verify(playerCache, never()).updatePlayerIfPresent(any(PlayerAuth.class));
        verify(commonService).send(player, MessageKey.ERROR);
    }

    @Test
    void shouldUpdateCachedPasswordAfterSuccessfulSave() {
        Player player = mock(Player.class);
        given(player.getName()).willReturn("Bobby");
        PlayerAuth auth = mock(PlayerAuth.class);
        given(playerCache.getAuth("bobby")).willReturn(auth);
        given(passwordSecurity.comparePassword("old", null, "Bobby")).willReturn(true);
        HashedPassword hashedPassword = mock(HashedPassword.class);
        given(passwordSecurity.computeHash("new", "bobby")).willReturn(hashedPassword);
        given(dataSource.updatePassword("bobby", hashedPassword)).willReturn(true);

        asyncChangePassword.changePassword(player, "old", "new");

        verify(dataSource).updatePassword("bobby", hashedPassword);
        verify(auth).setPassword(hashedPassword);
        verify(playerCache).updatePlayerIfPresent(auth);
        verify(commonService).send(player, MessageKey.PASSWORD_CHANGED_SUCCESS);
    }

    @Test
    void shouldRefreshCachedAuthAfterAdminChangeForOnlinePlayer() {
        CommandSender sender = mock(CommandSender.class);
        String player = "bobby";
        String password = "newPass";
        PlayerAuth auth = mock(PlayerAuth.class);
        given(playerCache.isAuthenticated(player)).willReturn(true);
        given(playerCache.getAuth(player)).willReturn(auth);
        HashedPassword hashedPassword = mock(HashedPassword.class);
        given(passwordSecurity.computeHash(password, player)).willReturn(hashedPassword);
        given(dataSource.updatePassword(player, hashedPassword)).willReturn(true);

        asyncChangePassword.changePasswordAsAdmin(sender, player, password);

        verify(auth).setPassword(hashedPassword);
        verify(playerCache).updatePlayerIfPresent(auth);
        verify(commonService).send(sender, MessageKey.PASSWORD_CHANGED_SUCCESS);
    }

    @ParameterizedTest(name = "logged in={0}, save successful={1}")
    @MethodSource("changePasswordCases")
    void shouldUpdatePassword(boolean loggedIn, boolean saveSuccessful, MessageKey expectedMessage) {
        CommandSender sender = mock(CommandSender.class);
        String player = "my_user12";
        String password = "passPass";
        given(playerCache.isAuthenticated(player)).willReturn(loggedIn);
        if (!loggedIn) {
            given(dataSource.isAuthAvailable(player)).willReturn(true);
        }

        HashedPassword hashedPassword = mock(HashedPassword.class);
        given(passwordSecurity.computeHash(password, player)).willReturn(hashedPassword);
        given(dataSource.updatePassword(player, hashedPassword)).willReturn(saveSuccessful);

        asyncChangePassword.changePasswordAsAdmin(sender, player, password);

        verify(commonService).send(sender, expectedMessage);
        verify(passwordSecurity).computeHash(password, player);
        verify(dataSource).updatePassword(player, hashedPassword);
    }

    private static Stream<Arguments> changePasswordCases() {
        return Stream.of(
            Arguments.of(true, true, MessageKey.PASSWORD_CHANGED_SUCCESS),
            Arguments.of(false, true, MessageKey.PASSWORD_CHANGED_SUCCESS),
            Arguments.of(true, false, MessageKey.ERROR));
    }

}
