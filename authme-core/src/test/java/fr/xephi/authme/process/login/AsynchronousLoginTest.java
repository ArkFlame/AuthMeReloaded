package fr.xephi.authme.process.login;

import fr.xephi.authme.TestHelper;
import fr.xephi.authme.data.TempbanManager;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.data.auth.PlayerCache;
import fr.xephi.authme.data.captcha.LoginCaptchaManager;
import fr.xephi.authme.data.limbo.LimboMessageType;
import fr.xephi.authme.data.limbo.LimboPlayer;
import fr.xephi.authme.data.limbo.LimboPlayerState;
import fr.xephi.authme.data.limbo.LimboService;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.events.AuthMeAsyncPreLoginEvent;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.permission.PlayerStatePermission;
import fr.xephi.authme.platform.DialogAdapter;
import fr.xephi.authme.platform.DialogInputSpec;
import fr.xephi.authme.platform.DialogWindowSpec;
import fr.xephi.authme.security.PasswordSecurity;
import fr.xephi.authme.service.BukkitService;
import fr.xephi.authme.service.CancellableTask;
import fr.xephi.authme.service.CommonService;
import fr.xephi.authme.service.DialogStateService;
import fr.xephi.authme.service.DialogWindowService;
import fr.xephi.authme.service.SessionService;
import fr.xephi.authme.service.bungeecord.BungeeSender;
import fr.xephi.authme.settings.properties.DatabaseSettings;
import fr.xephi.authme.settings.properties.EmailSettings;
import fr.xephi.authme.settings.properties.HooksSettings;
import fr.xephi.authme.settings.properties.PluginSettings;
import fr.xephi.authme.settings.properties.RegistrationSettings;
import fr.xephi.authme.settings.properties.RestrictionSettings;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static fr.xephi.authme.listener.TestPlayerUtils.mockPlayerWithName;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Test for {@link AsynchronousLogin}.
 */
@ExtendWith(MockitoExtension.class)
class AsynchronousLoginTest {

    @InjectMocks
    @Spy
    private AsynchronousLogin asynchronousLogin;

    @Mock
    private DataSource dataSource;
    @Mock
    private PlayerCache playerCache;
    @Mock
    private CommonService commonService;
    @Mock
    private LimboService limboService;
    @Mock
    private BukkitService bukkitService;
    @Mock
    private PasswordSecurity passwordSecurity;
    @Mock
    private LoginCaptchaManager loginCaptchaManager;
    @Mock
    private TempbanManager tempbanManager;
    @Mock
    private SessionService sessionService;
    @Mock
    private BungeeSender bungeeSender;
    @Mock
    private DialogAdapter dialogAdapter;
    @Mock
    private DialogStateService dialogStateService;
    @Mock
    private DialogWindowService dialogWindowService;

    @BeforeAll
    static void initLogger() {
        TestHelper.setupLogger();
    }

    @Test
    void shouldNotForceLoginAlreadyLoggedInPlayer() {
        String name = "bobby";
        Player player = mockPlayerWithName(name);
        given(playerCache.isAuthenticated(name)).willReturn(true);

        asynchronousLogin.forceLogin(player);

        verify(playerCache, only()).isAuthenticated(name);
        verify(commonService).send(player, MessageKey.ALREADY_LOGGED_IN_ERROR);
        verifyNoInteractions(dataSource);
    }

    @ParameterizedTest(name = "inactive user={0}")
    @ValueSource(booleans = {false, true})
    void shouldNotForceLoginUnknownOrInactiveUser(boolean inactive) {
        String name = "oscar";
        Player player = mockPlayerWithName(name);
        given(playerCache.isAuthenticated(name)).willReturn(false);
        if (inactive) {
            int groupId = 13;
            PlayerAuth auth = PlayerAuth.builder().name(name).groupId(groupId).build();
            given(dataSource.getAuth(name)).willReturn(auth);
            given(commonService.getProperty(DatabaseSettings.MYSQL_COL_GROUP)).willReturn("group");
            given(commonService.getProperty(HooksSettings.NON_ACTIVATED_USERS_GROUP)).willReturn(groupId);
        } else {
            given(dataSource.getAuth(name)).willReturn(null);
        }

        asynchronousLogin.forceLogin(player);

        verify(playerCache, only()).isAuthenticated(name);
        verify(commonService).send(player, inactive ? MessageKey.ACCOUNT_NOT_ACTIVATED : MessageKey.UNKNOWN_USER);
        verify(dataSource, only()).getAuth(name);
    }

    @ParameterizedTest(name = "canceled event={0}")
    @ValueSource(booleans = {false, true})
    void shouldNotForceLoginWhenIpLimitReachedOrEventCanceled(boolean canceledEvent) {
        String name = "oscar";
        String ip = "1.1.1.245";
        Player player = mockPlayer(name, ip);
        given(playerCache.isAuthenticated(name)).willReturn(false);
        given(dataSource.getAuth(name)).willReturn(PlayerAuth.builder().name(name).build());
        given(commonService.getProperty(DatabaseSettings.MYSQL_COL_GROUP)).willReturn("");
        given(commonService.getProperty(PluginSettings.USE_ASYNC_TASKS)).willReturn(true);
        if (!canceledEvent) {
            doReturn(true).when(asynchronousLogin).hasReachedMaxLoggedInPlayersForIp(any(Player.class), anyString());
        }
        if (canceledEvent) {
            doAnswer((Answer<Void>) invocation -> {
                ((AuthMeAsyncPreLoginEvent) invocation.getArgument(0)).setCanLogin(false);
                return null;
            }).when(bukkitService).callEvent(any(AuthMeAsyncPreLoginEvent.class));
        }

        asynchronousLogin.forceLogin(player);

        verify(playerCache, only()).isAuthenticated(name);
        verify(dataSource, only()).getAuth(name);
        if (canceledEvent) {
            // A cancelled pre-login event short-circuits before the per-IP limit is evaluated
            verify(asynchronousLogin, never()).hasReachedMaxLoggedInPlayersForIp(any(Player.class), anyString());
        } else {
            verify(asynchronousLogin).hasReachedMaxLoggedInPlayersForIp(player, ip);
            verify(commonService).send(player, MessageKey.ALREADY_LOGGED_IN_ERROR);
        }
    }

    @ParameterizedTest(name = "checked IP {0}")
    @CsvSource({"1.1.1.1, false", "2.2.2.2, true"})
    void shouldEvaluateMaxLoginPerIpCheck(String checkedIp, boolean expectedExceeded) {
        Player player = mock(Player.class);
        given(commonService.getProperty(RestrictionSettings.MAX_LOGIN_PER_IP)).willReturn(2);
        given(commonService.hasPermission(player, PlayerStatePermission.ALLOW_MULTIPLE_ACCOUNTS)).willReturn(false);
        mockOnlinePlayersInBukkitService(checkedIp);

        boolean result = asynchronousLogin.hasReachedMaxLoggedInPlayersForIp(player, checkedIp);

        assertThat(result, equalTo(expectedExceeded));
        verify(commonService).hasPermission(player, PlayerStatePermission.ALLOW_MULTIPLE_ACCOUNTS);
        verify(bukkitService).getOnlinePlayers();
    }

    @ParameterizedTest(name = "threshold={0}, multiple accounts permission={1}")
    @CsvSource({"0, false, 2.2.2.2", "1, true, 1.1.1.1"})
    void shouldSkipIpCheck(int maxLoginPerIp, boolean hasMultipleAccountsPermission, String ip) {
        Player player = mock(Player.class);
        given(commonService.getProperty(RestrictionSettings.MAX_LOGIN_PER_IP)).willReturn(maxLoginPerIp);
        if (hasMultipleAccountsPermission) {
            given(commonService.hasPermission(player, PlayerStatePermission.ALLOW_MULTIPLE_ACCOUNTS)).willReturn(true);
        }

        boolean result = asynchronousLogin.hasReachedMaxLoggedInPlayersForIp(player, ip);

        assertThat(result, equalTo(false));
        if (hasMultipleAccountsPermission) {
            verify(commonService).hasPermission(player, PlayerStatePermission.ALLOW_MULTIPLE_ACCOUNTS);
        }
        verifyNoInteractions(bukkitService);
    }

    @Test
    void shouldNotResolveCallerIpAgainWhenCountingOnlinePlayers() {
        String ip = "1.1.1.245";
        Player player = mockPlayerWithName("albania");
        given(commonService.getProperty(RestrictionSettings.MAX_LOGIN_PER_IP)).willReturn(2);
        given(commonService.hasPermission(player, PlayerStatePermission.ALLOW_MULTIPLE_ACCOUNTS)).willReturn(false);
        given(bukkitService.getOnlinePlayers()).willReturn(List.of(player));
        clearInvocations(player);

        boolean result = asynchronousLogin.hasReachedMaxLoggedInPlayersForIp(player, ip);

        assertThat(result, equalTo(false));
        verify(player, never()).getAddress();
    }

    @ParameterizedTest(name = "dialogs enabled={0}")
    @ValueSource(booleans = {true, false})
    void shouldHandleTotpDialogAccordingToAvailability(boolean dialogsEnabled) {
        Player player = mockPlayer("bobby", "203.0.113.5");
        PlayerAuth auth = PlayerAuth.builder().name("bobby").totpKey("secret").build();
        LimboPlayer limboPlayer = mock(LimboPlayer.class);
        given(playerCache.isAuthenticated("bobby")).willReturn(false, false);
        givenExistingPlayerWithValidPassword(auth);
        given(limboService.getLimboPlayer(player)).willReturn(limboPlayer);
        given(commonService.getProperty(RegistrationSettings.USE_DIALOG_UI)).willReturn(dialogsEnabled);
        if (dialogsEnabled) {
            given(dialogAdapter.isDialogSupported()).willReturn(true);
            given(player.isOnline()).willReturn(true);
            given(dialogWindowService.createTotpDialog(player)).willReturn(createDialogSpec("2FA", "Verify"));
            doAnswer(invocation -> {
                invocation.<Runnable>getArgument(1).run();
                return mock(CancellableTask.class);
            }).when(bukkitService).runTaskLater(eq(player), any(Runnable.class), eq(1L));
        } else {
            given(commonService.getProperty(RegistrationSettings.USE_PREJOIN_DIALOG_UI)).willReturn(false);
        }

        asynchronousLogin.login(player, "hunter2");

        verify(limboService).resetMessageTask(player, LimboMessageType.TOTP_CODE);
        verify(limboPlayer).setState(LimboPlayerState.TOTP_REQUIRED);
        if (dialogsEnabled) {
            verify(dialogAdapter).showTotpDialog(eq(player), any(DialogWindowSpec.class));
            verify(asynchronousLogin, never()).performLogin(player, auth);
        } else {
            verify(dialogAdapter, never()).showTotpDialog(eq(player), any(DialogWindowSpec.class));
        }
    }

    @Test
    void shouldNotLogInTotpPlayerBeforeLimboIsCreated() {
        Player player = mockPlayer("bobby", "203.0.113.5");
        PlayerAuth auth = PlayerAuth.builder().name("bobby").totpKey("secret").build();
        given(playerCache.isAuthenticated("bobby")).willReturn(false);
        givenExistingPlayerWithValidPassword(auth);
        given(limboService.getLimboPlayer(player)).willReturn(null);

        asynchronousLogin.login(player, "hunter2");

        verify(commonService).send(player, MessageKey.LOGIN_MESSAGE);
        verify(asynchronousLogin, never()).performLogin(player, auth);
    }

    @Test
    void shouldCheckPasswordBeforeEnforcingIpLimit() {
        Player player = mockPlayer("bobby", "203.0.113.5");
        PlayerAuth auth = PlayerAuth.builder().name("bobby").build();
        given(playerCache.isAuthenticated("bobby")).willReturn(false);
        given(dataSource.getAuth("bobby")).willReturn(auth);
        given(commonService.getProperty(DatabaseSettings.MYSQL_COL_GROUP)).willReturn("");
        given(commonService.getProperty(PluginSettings.USE_ASYNC_TASKS)).willReturn(false);
        given(loginCaptchaManager.isCaptchaRequired("bobby")).willReturn(false);
        given(passwordSecurity.comparePassword("hunter2", auth.getPassword(), "bobby")).willReturn(true);
        doReturn(true).when(asynchronousLogin).hasReachedMaxLoggedInPlayersForIp(player, "203.0.113.5");

        asynchronousLogin.login(player, "hunter2");

        InOrder inOrder = inOrder(passwordSecurity, asynchronousLogin);
        inOrder.verify(passwordSecurity).comparePassword("hunter2", auth.getPassword(), "bobby");
        inOrder.verify(asynchronousLogin).hasReachedMaxLoggedInPlayersForIp(player, "203.0.113.5");
        verify(commonService).send(player, MessageKey.ALREADY_LOGGED_IN_ERROR);
        verify(asynchronousLogin, never()).performLogin(player, auth);
    }

    @Test
    void shouldIgnoreConcurrentLoginForSamePlayer() {
        Player player = mockPlayer("bobby", "203.0.113.5");
        PlayerAuth auth = PlayerAuth.builder().name("bobby").build();
        given(playerCache.isAuthenticated("bobby")).willReturn(false);
        given(dataSource.getAuth("bobby")).willReturn(auth);
        given(commonService.getProperty(DatabaseSettings.MYSQL_COL_GROUP)).willReturn("");
        given(commonService.getProperty(RestrictionSettings.MAX_LOGIN_PER_IP)).willReturn(0);
        given(commonService.getProperty(PluginSettings.USE_ASYNC_TASKS)).willReturn(false);
        given(loginCaptchaManager.isCaptchaRequired("bobby")).willReturn(false);
        AtomicBoolean reentered = new AtomicBoolean(false);
        given(passwordSecurity.comparePassword("hunter2", auth.getPassword(), "bobby")).willAnswer(invocation -> {
            if (reentered.compareAndSet(false, true)) {
                // Simulate a second /login arriving while the password check is still running
                asynchronousLogin.login(player, "hunter2");
            }
            return true;
        });

        asynchronousLogin.login(player, "hunter2");

        verify(passwordSecurity, only()).comparePassword("hunter2", auth.getPassword(), "bobby");
    }

    @Test
    void shouldNotLogInPlayerAlreadyAuthenticatedByConcurrentAttempt() {
        Player player = mockPlayerWithName("bobby");
        // Lenient: the guard returns before the online check, so this stub may go unused
        lenient().when(player.isOnline()).thenReturn(true);
        PlayerAuth auth = PlayerAuth.builder().name("bobby").build();
        given(playerCache.isAuthenticated("bobby")).willReturn(true);

        asynchronousLogin.performLogin(player, auth);

        verify(playerCache).isAuthenticated("bobby");
        verify(player, never()).isOnline();
        verify(playerCache, never()).updatePlayer(auth);
        verify(dataSource, never()).updateSession(auth);
        verify(dataSource, never()).setLogged("bobby");
    }

    @Test
    void shouldRevertLoginWhenPlayerQuitDuringLoginProcess() {
        Player player = mockPlayer("bobby", "203.0.113.5");
        PlayerAuth auth = PlayerAuth.builder().name("bobby").build();
        given(player.isOnline()).willReturn(true, false);
        given(commonService.getProperty(RestrictionSettings.DISPLAY_OTHER_ACCOUNTS)).willReturn(false);
        given(commonService.getProperty(EmailSettings.RECALL_PLAYERS)).willReturn(false);

        asynchronousLogin.performLogin(player, auth);

        verify(playerCache).updatePlayer(auth);
        verify(playerCache).removePlayer("bobby");
        verify(dataSource).setUnlogged("bobby");
        verify(sessionService).revokeSession("bobby");
        verify(bungeeSender, never()).isEnabled();
    }

    private void givenExistingPlayerWithValidPassword(PlayerAuth auth) {
        given(dataSource.getAuth("bobby")).willReturn(auth);
        given(commonService.getProperty(DatabaseSettings.MYSQL_COL_GROUP)).willReturn("");
        given(commonService.getProperty(RestrictionSettings.MAX_LOGIN_PER_IP)).willReturn(0);
        given(commonService.getProperty(PluginSettings.USE_ASYNC_TASKS)).willReturn(false);
        given(loginCaptchaManager.isCaptchaRequired("bobby")).willReturn(false);
        given(passwordSecurity.comparePassword("hunter2", auth.getPassword(), "bobby")).willReturn(true);
    }

    private static Player mockPlayer(String name, String ip) {
        Player player = mockPlayerWithName(name);
        TestHelper.mockIpAddressToPlayer(player, ip);
        return player;
    }

    private static DialogWindowSpec createDialogSpec(String title, String buttonLabel) {
        return new DialogWindowSpec(title,
            List.of(new DialogInputSpec("code", "Code", 16)),
            buttonLabel,
            "Cancel",
            false,
            false,
            null);
    }

    private void mockOnlinePlayersInBukkitService(String checkedIp) {
        Player primaryOnline;
        Player primaryOffline;
        Player extraSameIpOnline = mock(Player.class);

        if ("1.1.1.1".equals(checkedIp)) {
            primaryOnline = mockPlayer("albania", "1.1.1.1");
            given(dataSource.isLogged("albania")).willReturn(true);

            primaryOffline = mockPlayer("brazil", "1.1.1.1");
            given(dataSource.isLogged("brazil")).willReturn(false);

            TestHelper.mockIpAddressToPlayer(extraSameIpOnline, "2.2.2.2");
        } else {
            primaryOnline = mockPlayer("congo", "2.2.2.2");
            given(dataSource.isLogged("congo")).willReturn(true);

            primaryOffline = mockPlayer("denmark", "2.2.2.2");
            given(dataSource.isLogged("denmark")).willReturn(false);

            Player playerE = mockPlayer("ecuador", "2.2.2.2");
            given(dataSource.isLogged("ecuador")).willReturn(true);
            extraSameIpOnline = playerE;
        }

        Player otherIpPlayer = mock(Player.class);
        TestHelper.mockIpAddressToPlayer(otherIpPlayer, "3.3.3.3");

        List<Player> onlinePlayers = Arrays.asList(primaryOnline, primaryOffline, extraSameIpOnline, otherIpPlayer);
        given(bukkitService.getOnlinePlayers()).willReturn(onlinePlayers);
    }
}
