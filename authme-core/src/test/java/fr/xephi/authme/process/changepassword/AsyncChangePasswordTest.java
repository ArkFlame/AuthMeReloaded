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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.verification.VerificationMode;

import java.util.stream.Stream;

import static fr.xephi.authme.listener.TestPlayerUtils.mockPlayerWithName;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.times;
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

    @BeforeAll
    static void setUpLogger() {
        TestHelper.setupLogger();
    }

    @Test
    void shouldRejectCommandForUnknownUser() {
        CommandSender sender = mock(CommandSender.class);
        String player = "player";
        given(playerCache.isAuthenticated(player)).willReturn(false);
        given(dataSource.isAuthAvailable(player)).willReturn(false);

        asyncChangePassword.changePasswordAsAdmin(sender, player, "password");

        verify(commonService).send(sender, MessageKey.UNKNOWN_USER);
        verify(dataSource, only()).isAuthAvailable(player);
    }

    @Test
    void shouldDoNothingIfPlayerIsNoLongerLoggedIn() {
        Player player = mockPlayerWithName("Bobby");
        given(playerCache.getAuth("bobby")).willReturn(null);

        asyncChangePassword.changePassword(player, "old", "new");

        verifyNoInteractions(passwordSecurity, dataSource, commonService);
    }

    @ParameterizedTest(name = "save successful={0}")
    @ValueSource(booleans = {true, false})
    void shouldOnlyUpdateCacheAfterSuccessfulSave(boolean saveSuccessful) {
        Player player = mockPlayerWithName("Bobby");
        PlayerAuth auth = mock(PlayerAuth.class);
        HashedPassword hashedPassword = new HashedPassword("new_hash");
        given(playerCache.getAuth("bobby")).willReturn(auth);
        given(passwordSecurity.comparePassword("old", null, "Bobby")).willReturn(true);
        given(passwordSecurity.computeHash("new", "bobby")).willReturn(hashedPassword);
        given(dataSource.updatePassword("bobby", hashedPassword)).willReturn(saveSuccessful);

        asyncChangePassword.changePassword(player, "old", "new");

        VerificationMode cached = saveSuccessful ? times(1) : never();
        verify(dataSource).updatePassword("bobby", hashedPassword);
        verify(auth, cached).setPassword(hashedPassword);
        verify(playerCache, cached).updatePlayerIfPresent(auth);
        verify(commonService).send(player,
            saveSuccessful ? MessageKey.PASSWORD_CHANGED_SUCCESS : MessageKey.ERROR);
    }

    @Test
    void shouldRefreshCachedAuthAfterAdminChangeForOnlinePlayer() {
        CommandSender sender = mock(CommandSender.class);
        String player = "bobby";
        String password = "newPass";
        PlayerAuth auth = mock(PlayerAuth.class);
        given(playerCache.isAuthenticated(player)).willReturn(true);
        given(playerCache.getAuth(player)).willReturn(auth);
        HashedPassword hashedPassword = new HashedPassword("new_hash");
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
        given(playerCache.isAuthenticated(player)).willReturn(loggedIn);
        if (!loggedIn) {
            given(dataSource.isAuthAvailable(player)).willReturn(true);
        }

        HashedPassword hashedPassword = new HashedPassword("hash");
        given(passwordSecurity.computeHash("passPass", player)).willReturn(hashedPassword);
        given(dataSource.updatePassword(player, hashedPassword)).willReturn(saveSuccessful);

        asyncChangePassword.changePasswordAsAdmin(sender, player, "passPass");

        verify(commonService).send(sender, expectedMessage);
        verify(passwordSecurity).computeHash("passPass", player);
        verify(dataSource).updatePassword(player, hashedPassword);
    }

    private static Stream<Arguments> changePasswordCases() {
        return Stream.of(
            Arguments.of(true, true, MessageKey.PASSWORD_CHANGED_SUCCESS),
            Arguments.of(false, true, MessageKey.PASSWORD_CHANGED_SUCCESS),
            Arguments.of(true, false, MessageKey.ERROR));
    }
}
