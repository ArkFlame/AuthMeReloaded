package fr.xephi.authme.process.join;

import fr.xephi.authme.TestHelper;
import fr.xephi.authme.data.ProxySessionManager;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.data.auth.PlayerCache;
import fr.xephi.authme.data.limbo.LimboService;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.platform.DialogAdapter;
import fr.xephi.authme.process.login.AsynchronousLogin;
import fr.xephi.authme.process.register.AsyncRegister;
import fr.xephi.authme.service.BukkitService;
import fr.xephi.authme.service.CommonService;
import fr.xephi.authme.service.DialogStateService;
import fr.xephi.authme.service.DialogWindowService;
import fr.xephi.authme.service.FloodgateService;
import fr.xephi.authme.service.PendingPremiumCache;
import fr.xephi.authme.service.PluginHookService;
import fr.xephi.authme.service.PreJoinDialogService;
import fr.xephi.authme.service.PremiumLoginVerifier;
import fr.xephi.authme.service.PremiumService;
import fr.xephi.authme.service.ProxyLoginRequestValidator;
import fr.xephi.authme.service.SessionService;
import fr.xephi.authme.service.ValidationService;
import fr.xephi.authme.service.bungeecord.BungeeSender;
import fr.xephi.authme.settings.WelcomeMessageConfiguration;
import fr.xephi.authme.settings.commandconfig.CommandManager;
import fr.xephi.authme.settings.properties.HooksSettings;
import fr.xephi.authme.settings.properties.PremiumSettings;
import fr.xephi.authme.settings.properties.RegistrationSettings;
import fr.xephi.authme.settings.properties.RestrictionSettings;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collections;
import java.util.Locale;
import java.util.function.Function;

import static fr.xephi.authme.service.BukkitServiceTestHelper.setBukkitServiceToRunTaskLater;
import static fr.xephi.authme.service.BukkitServiceTestHelper.setBukkitServiceToRunTaskOptionallyAsync;
import static fr.xephi.authme.service.BukkitServiceTestHelper.setBukkitServiceToScheduleSyncEntityTaskFromOptionallyAsyncTask;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Guards the single-account-lookup promise of the join pipeline: whichever branch handles a join,
 * the account is read once and then reused from the loaded {@link PlayerAuth}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.WARN)
class AsynchronousJoinQueryCountTest {

    @InjectMocks
    private AsynchronousJoin asynchronousJoin;

    @Mock
    private DataSource database;
    @Mock
    private CommonService service;
    @Mock
    private LimboService limboService;
    @Mock
    private PluginHookService pluginHookService;
    @Mock
    private BukkitService bukkitService;
    @Mock
    private AsynchronousLogin asynchronousLogin;
    @Mock
    private AsyncRegister asyncRegister;
    @Mock
    private CommandManager commandManager;
    @Mock
    private ValidationService validationService;
    @Mock
    private WelcomeMessageConfiguration welcomeMessageConfiguration;
    @Mock
    private SessionService sessionService;
    @Mock
    private PlayerCache playerCache;
    @Mock
    private BungeeSender bungeeSender;
    @Mock
    private ProxySessionManager proxySessionManager;
    @Mock
    private DialogAdapter dialogAdapter;
    @Mock
    private DialogWindowService dialogWindowService;
    @Mock
    private DialogStateService dialogStateService;
    @Mock
    private PreJoinDialogService preJoinDialogService;
    @Mock
    private PremiumLoginVerifier premiumLoginVerifier;
    @Mock
    private PendingPremiumCache pendingPremiumCache;
    @Mock
    private PremiumService premiumService;
    @Mock
    private FloodgateService floodgateService;
    @Mock
    private ProxyLoginRequestValidator proxyLoginRequestValidator;

    @BeforeAll
    static void initLogger() {
        TestHelper.setupLogger();
    }

    @BeforeEach
    void setUp() {
        setBukkitServiceToScheduleSyncEntityTaskFromOptionallyAsyncTask(bukkitService);
        setBukkitServiceToRunTaskOptionallyAsync(bukkitService);
        setBukkitServiceToRunTaskLater(bukkitService);
        given(service.getProperty(PremiumSettings.ENABLE_PREMIUM)).willReturn(false);
        givenEventsAreCalledWithoutListeners();
        given(preJoinDialogService.consumeSession(anyString()))
            .willReturn(PreJoinDialogService.PendingDialogState.NONE);
    }

    @Test
    void shouldUseSingleLookupForSessionResume() {
        Player player = newPlayer("Bobby");
        givenRegisteredJoin(player);
        given(sessionService.canResumeSession(player)).willReturn(true);

        asynchronousJoin.processJoin(player);

        verify(database, times(1)).getAuth("bobby");
        verify(database, never()).isAuthAvailable(anyString());
        verify(commandManager).runCommandsOnSessionLogin(player);
        verify(asynchronousLogin).forceLogin(player);
        verify(limboService, never()).createLimboPlayer(eq(player), anyBoolean());
    }

    @Test
    void shouldUseSingleLookupForPendingLogin() {
        Player player = newPlayer("Bobby");
        givenRegisteredJoin(player);
        given(preJoinDialogService.consumeSession("bobby"))
            .willReturn(new PreJoinDialogService.PendingDialogState("hunter2", null, null, false, false, null));
        given(service.getProperty(RestrictionSettings.LOGIN_TIMEOUT)).willReturn(30);
        given(service.getProperty(RegistrationSettings.APPLY_BLIND_EFFECT)).willReturn(false);
        given(service.getProperty(RegistrationSettings.USE_DIALOG_UI)).willReturn(false);
        given(pluginHookService.isEssentialsAvailable()).willReturn(false);

        asynchronousJoin.processJoin(player);

        verify(database, times(1)).getAuth("bobby");
        verify(database, never()).isAuthAvailable(anyString());
        verify(asynchronousLogin).login(player, "hunter2");
        verify(limboService).createLimboPlayer(player, true);
    }

    @Test
    void shouldUseSingleLookupForFloodgateAutoLogin() {
        Player player = newPlayer("Bobby");
        givenRegisteredJoin(player);
        given(floodgateService.canAutoLogin(any())).willReturn(true);

        asynchronousJoin.processJoin(player);

        verify(database, times(1)).getAuth("bobby");
        verify(database, never()).isAuthAvailable(anyString());
        verify(asynchronousLogin).forceLogin(player);
        verify(limboService, never()).createLimboPlayer(eq(player), anyBoolean());
    }

    private void givenRegisteredJoin(Player player) {
        given(validationService.fulfillsNameRestrictions(player)).willReturn(true);
        given(service.getProperty(RestrictionSettings.UNRESTRICTED_NAMES)).willReturn(Collections.emptySet());
        given(service.getProperty(RestrictionSettings.FORCE_SURVIVAL_MODE)).willReturn(false);
        given(service.getProperty(HooksSettings.DISABLE_SOCIAL_SPY)).willReturn(false);
        given(service.getProperty(RestrictionSettings.MAX_JOIN_PER_IP)).willReturn(0);
        given(service.getProperty(RestrictionSettings.PROTECT_INVENTORY_BEFORE_LOGIN)).willReturn(false);
        given(database.getAuth(player.getName().toLowerCase(Locale.ROOT))).willReturn(mock(PlayerAuth.class));
    }

    private static Player newPlayer(String name) {
        Player player = mock(Player.class);
        given(player.getName()).willReturn(name);
        given(player.isOnline()).willReturn(true);
        TestHelper.mockIpAddressToPlayer(player, "127.0.0.1");
        return player;
    }

    @SuppressWarnings("unchecked")
    private void givenEventsAreCalledWithoutListeners() {
        given(bukkitService.createAndCallEvent(any())).willAnswer(invocation ->
            ((Function<Boolean, Event>) invocation.getArgument(0)).apply(false));
    }
}
