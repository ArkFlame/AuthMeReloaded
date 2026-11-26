package fr.xephi.authme.process.join;

import fr.xephi.authme.TestHelper;
import fr.xephi.authme.data.ProxySessionManager;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.data.auth.PlayerCache;
import fr.xephi.authme.data.limbo.LimboService;
import fr.xephi.authme.events.PlayerPreAuthEvent;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.platform.DialogAdapter;
import fr.xephi.authme.platform.DialogInputSpec;
import fr.xephi.authme.platform.DialogWindowSpec;
import fr.xephi.authme.process.login.AsynchronousLogin;
import fr.xephi.authme.process.register.AsyncRegister;
import fr.xephi.authme.process.register.executors.ApiPasswordRegisterParams;
import fr.xephi.authme.process.register.executors.RegistrationMethod;
import fr.xephi.authme.service.BukkitService;
import fr.xephi.authme.service.CancellableTask;
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
import fr.xephi.authme.settings.properties.SecuritySettings;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import static fr.xephi.authme.service.BukkitServiceTestHelper.setBukkitServiceToRunTaskLater;
import static fr.xephi.authme.service.BukkitServiceTestHelper.setBukkitServiceToRunTaskOptionallyAsync;
import static fr.xephi.authme.service.BukkitServiceTestHelper.setBukkitServiceToScheduleSyncEntityTaskFromOptionallyAsyncTask;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.WARN)
public class AsynchronousJoinTest {

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

    private PlayerAuth auth;

    @BeforeAll
    public static void initLogger() {
        TestHelper.setupLogger();
    }

    @BeforeEach
    public void setUp() {
        setBukkitServiceToScheduleSyncEntityTaskFromOptionallyAsyncTask(bukkitService);
        setBukkitServiceToRunTaskOptionallyAsync(bukkitService);
        setBukkitServiceToRunTaskLater(bukkitService);
        given(service.getProperty(PremiumSettings.ENABLE_PREMIUM)).willReturn(false);
        givenEventsAreCalledWithoutListeners();
        given(preJoinDialogService.consumeSession(anyString()))
            .willReturn(PreJoinDialogService.PendingDialogState.NONE);
    }

    @Test
    public void shouldShowLoginDialogForUnauthenticatedRegisteredPlayer() {
        Player player = givenRegisteredPlayer();
        given(playerCache.isAuthenticated("Bobby")).willReturn(false);
        givenDialogUi(player);

        asynchronousJoin.processJoin(player);

        verify(limboService).createLimboPlayer(player, true);
        verify(bukkitService).runTaskLater(eq(player), any(Runnable.class), eq(1L));
        verify(dialogAdapter).showLoginDialog(eq(player), any(DialogWindowSpec.class));
    }

    @Test
    public void shouldNotShowLoginDialogForAlreadyAuthenticatedPlayer() {
        Player player = givenRegisteredPlayer();
        given(playerCache.isAuthenticated("Bobby")).willReturn(true);

        asynchronousJoin.processJoin(player);

        verify(limboService, never()).createLimboPlayer(eq(player), eq(true));
        verifyNoLoginDialog(player);
    }

    @Test
    public void shouldNotShowDelayedDialogIfPlayerGetsAuthenticatedBeforeTaskRuns() {
        Player player = givenRegisteredPlayer();
        given(player.isOnline()).willReturn(true);
        given(playerCache.isAuthenticated("Bobby")).willReturn(false, false, true);
        givenDialogUi(player);

        AtomicReference<Runnable> delayedTask = new AtomicReference<>();
        doAnswer(invocation -> {
            delayedTask.set(invocation.getArgument(1));
            return mock(CancellableTask.class);
        }).when(bukkitService).runTaskLater(eq(player), any(Runnable.class), eq(1L));

        asynchronousJoin.processJoin(player);
        delayedTask.get().run();

        verify(limboService).createLimboPlayer(player, true);
        verifyNoLoginDialog(player);
    }

    @Test
    public void shouldResumeSessionWithoutOpeningDialog() {
        Player player = givenRegisteredPlayer();
        given(sessionService.canResumeSession(player)).willReturn(true);

        asynchronousJoin.processJoin(player);

        verify(service).send(player, MessageKey.SESSION_RECONNECTION);
        verify(commandManager).runCommandsOnSessionLogin(player);
        verify(asynchronousLogin).forceLogin(player);
        verify(limboService, never()).createLimboPlayer(player, true);
        verifyNoLoginDialog(player);
        verifySingleAuthLookup("bobby");
    }

    @Test
    public void shouldAutoLoginFromProxySessionWithoutCreatingLimbo() {
        Player player = givenRegisteredPlayer();
        given(proxySessionManager.consumeLoginRequest("bobby"))
            .willReturn(new ProxySessionManager.ProxyLoginRequest("bobby", null));
        given(proxyLoginRequestValidator.validate(player, null)).willReturn(true);

        asynchronousJoin.processJoin(player);

        verify(service).send(player, MessageKey.SESSION_RECONNECTION);
        verify(commandManager).runCommandsOnSessionLogin(player);
        verify(asynchronousLogin).forceLoginFromProxy(player);
        verify(asynchronousLogin, never()).forceLogin(player);
        verify(limboService, never()).createLimboPlayer(player, true);
    }

    @Test
    public void shouldAutoLoginFromProxySessionEvenIfPreJoinDialogWasCancelled() {
        Player player = givenRegisteredPlayer();
        givenPendingDialogState(new PreJoinDialogService.PendingDialogState(
            null, null, null, false, false, "You have canceled the login."));
        given(proxySessionManager.consumeLoginRequest("bobby"))
            .willReturn(new ProxySessionManager.ProxyLoginRequest("bobby", null));
        given(proxyLoginRequestValidator.validate(player, null)).willReturn(true);

        asynchronousJoin.processJoin(player);

        verify(asynchronousLogin).forceLoginFromProxy(player);
        verify(player, never()).kickPlayer(any());
        verify(limboService, never()).createLimboPlayer(eq(player), eq(true));
    }

    @Test
    public void shouldProcessPendingPreJoinLoginInsteadOfShowingDialog() {
        Player player = givenRegisteredPlayer();
        givenPendingDialogState(new PreJoinDialogService.PendingDialogState(
            "hunter2", null, null, false, false, null));
        given(playerCache.isAuthenticated("Bobby")).willReturn(false);

        asynchronousJoin.processJoin(player);

        verify(limboService).createLimboPlayer(player, true);
        verify(asynchronousLogin).login(player, "hunter2");
        verifyNoLoginDialog(player);
        verifySingleAuthLookup("bobby");
    }

    @ParameterizedTest(name = "queued proxy premium request={0}")
    @ValueSource(booleans = {true, false})
    public void shouldHandlePendingPremiumEnrollmentOnJoin(boolean hasQueuedRequest) {
        Player player = givenRegisteredPlayer();
        given(service.getProperty(PremiumSettings.ENABLE_PREMIUM)).willReturn(true);
        given(bungeeSender.isEnabled()).willReturn(true);
        UUID offlineUuid = UUID.fromString("f0647d73-8421-3979-bdb6-6b88dc3d03d4");
        given(player.getUniqueId()).willReturn(offlineUuid);

        given(auth.isPremium()).willReturn(false);

        UUID mojangUuid = UUID.randomUUID();
        given(proxySessionManager.getLoginRequest("Bobby")).willReturn(
            hasQueuedRequest ? new ProxySessionManager.ProxyLoginRequest("bobby", mojangUuid) : null);
        if (hasQueuedRequest) {
            given(pendingPremiumCache.removePending("Bobby")).willReturn(mojangUuid);
        }

        asynchronousJoin.processJoin(player);

        verify(service, never()).send(eq(player), eq(MessageKey.PREMIUM_PENDING_FAIL));
        verify(bungeeSender, never()).sendPremiumUnset("Bobby");
        if (hasQueuedRequest) {
            verify(premiumService).finalizePendingPremium(player, mojangUuid);
            verify(asynchronousLogin).forceLoginFromProxy(player);
            verify(limboService, never()).createLimboPlayer(player, true);
        } else {
            verify(pendingPremiumCache, never()).removePending("Bobby");
            verify(premiumService, never()).finalizePendingPremium(any(), any());
            verify(limboService).createLimboPlayer(player, true);
        }
    }

    @Test
    public void shouldFallThroughToSessionOrLimboWhenProxyValidationFails() {
        Player player = givenRegisteredPlayer();
        given(proxySessionManager.consumeLoginRequest("bobby"))
            .willReturn(new ProxySessionManager.ProxyLoginRequest("bobby", UUID.randomUUID()));
        given(proxyLoginRequestValidator.validate(eq(player), any())).willReturn(false);

        asynchronousJoin.processJoin(player);

        verify(limboService).createLimboPlayer(player, true);
        verify(asynchronousLogin, never()).forceLoginFromProxy(player);
    }

    @Test
    public void shouldForceLoginPlayerApprovedViaPreJoinDialog() {
        Player player = givenRegisteredPlayer();
        givenPendingDialogState(new PreJoinDialogService.PendingDialogState(
            null, null, null, false, true, null));

        asynchronousJoin.processJoin(player);

        verify(limboService).createLimboPlayer(player, true);
        verify(asynchronousLogin).forceLogin(player);
        verify(asynchronousLogin, never()).login(eq(player), any());
        verify(dialogAdapter, never()).showLoginDialog(eq(player), any());
    }

    @Test
    public void shouldSkipPostJoinDialogWhenPreJoinDialogWasDeferred() {
        Player player = givenRegisteredPlayer();
        givenPendingDialogState(new PreJoinDialogService.PendingDialogState(
            null, null, null, true, false, null));
        given(playerCache.isAuthenticated("Bobby")).willReturn(false);
        givenDialogUi(player);

        asynchronousJoin.processJoin(player);

        verify(limboService).createLimboPlayer(player, true);
        verifyNoLoginDialog(player);
    }

    @Test
    public void shouldNotCreateLimboForPlayerWhoQuitBeforeSyncTaskRuns() {
        Player player = givenRegisteredPlayer();
        given(player.isOnline()).willReturn(false);

        asynchronousJoin.processJoin(player);

        verify(limboService, never()).createLimboPlayer(eq(player), anyBoolean());
    }

    @Test
    public void shouldResolvePlayerSocketAddressOnlyOncePerJoin() {
        Player player = givenRegisteredPlayer();
        clearInvocations(player);

        asynchronousJoin.processJoin(player);

        // The socket address must be fetched once and reused for the IP, not looked up again by PlayerUtils.
        verify(player, times(1)).getAddress();
    }

    @Test
    public void shouldHandleJoinWhenPlayerSocketAddressIsGone() {
        // The player disconnected after the join event but before this async task ran,
        // so Bukkit no longer exposes the socket address (getAddress() == null).
        Player player = givenRegisteredPlayer();
        given(player.getAddress()).willReturn(null);

        asynchronousJoin.processJoin(player);

        verify(limboService).createLimboPlayer(player, true);
    }

    @Test
    public void shouldRestoreOperatorOfUnregisteredPlayerWhenRegistrationIsOptional() {
        Player player = mockPlayer("Bobby");
        givenDefaultJoinRestrictions(player);
        given(database.getAuth("bobby")).willReturn(null);
        given(service.getProperty(RegistrationSettings.FORCE)).willReturn(false);

        asynchronousJoin.processJoin(player);

        verify(limboService).restorePendingOperator(player);
        verify(limboService, never()).createLimboPlayer(eq(player), anyBoolean());
    }

    @Test
    public void shouldAutoLoginRegisteredFloodgatePlayerWithoutCreatingLimbo() {
        Player player = mockPlayer(".Bobby");
        UUID playerId = UUID.fromString("00000000-0000-0000-0009-01f163ba2c3a");
        given(player.getUniqueId()).willReturn(playerId);
        setUpRegisteredJoin(player);
        given(floodgateService.canAutoLogin(playerId)).willReturn(true);

        asynchronousJoin.processJoin(player);

        verify(asynchronousLogin).forceLogin(player);
        verify(limboService, never()).createLimboPlayer(eq(player), anyBoolean());
        verifyNoLoginDialog(player);
        // The account is read once and reused; no second availability lookup is performed.
        verifySingleAuthLookup(".bobby");
    }

    @Test
    public void shouldAutoRegisterUnregisteredFloodgatePlayer() {
        Player player = mockPlayer(".Bobby");
        UUID playerId = UUID.fromString("00000000-0000-0000-0009-01f163ba2c3a");
        given(player.getUniqueId()).willReturn(playerId);
        givenDefaultJoinRestrictions(player);
        given(database.getAuth(".bobby")).willReturn(null);
        given(floodgateService.canAutoRegister(playerId)).willReturn(true);
        given(service.getProperty(SecuritySettings.MIN_PASSWORD_LENGTH)).willReturn(5);
        given(service.getProperty(SecuritySettings.MAX_PASSWORD_LENGTH)).willReturn(30);

        asynchronousJoin.processJoin(player);

        ArgumentCaptor<ApiPasswordRegisterParams> params = ArgumentCaptor.forClass(ApiPasswordRegisterParams.class);
        verify(asyncRegister).register(eq(RegistrationMethod.API_REGISTRATION), params.capture());
        assertThat(params.getValue().getPlayer(), is(player));
        assertThat(params.getValue().getPassword().length(), is(24));
        assertThat(params.getValue().getLoginAfterRegister(), is(true));
        verify(limboService, never()).createLimboPlayer(eq(player), anyBoolean());
    }

    private void givenPendingDialogState(PreJoinDialogService.PendingDialogState state) {
        given(preJoinDialogService.consumeSession("bobby")).willReturn(state);
    }

    @Test
    public void shouldForceLoginWithoutPromptWhenListenerRequestsIt() {
        Player player = givenRegisteredPlayer();
        given(auth.getTotpKey()).willReturn("TOTPSECRET");
        AtomicReference<PlayerPreAuthEvent> seen = new AtomicReference<>();
        listenToPreAuth(event -> {
            seen.set(event);
            event.forceLogin();
        });

        asynchronousJoin.processJoin(player);

        assertThat(seen.get().isAsynchronous(), is(false));
        InOrder order = inOrder(bukkitService);
        order.verify(bukkitService).scheduleSyncTaskFromOptionallyAsyncTask(eq(player), any(Runnable.class));
        order.verify(bukkitService).callEvent(seen.get());
        order.verify(bukkitService).runTaskOptionallyAsync(any(Runnable.class));
        verify(asynchronousLogin).performLogin(player, auth);
        verify(asynchronousLogin, never()).forceLogin(player);
        verify(asynchronousLogin, never()).login(eq(player), anyString());
        verifySingleAuthLookup("bobby");
        verify(limboService, never()).createLimboPlayer(eq(player), anyBoolean());
        verify(sessionService, never()).canResumeSession(player);
        verify(bukkitService, never()).createAndCallEvent(any());
    }

    @Test
    public void shouldSkipPromptWhenListenerCancelsPreAuthEvent() {
        Player player = givenRegisteredPlayer();
        AtomicReference<PlayerPreAuthEvent> seen = new AtomicReference<>();
        listenToPreAuth(event -> {
            seen.set(event);
            event.skipAuthentication();
        });

        asynchronousJoin.processJoin(player);

        assertThat(seen.get().isAsynchronous(), is(false));
        verify(limboService, never()).createLimboPlayer(eq(player), anyBoolean());
        verify(asynchronousLogin, never()).forceLogin(player);
        verify(asynchronousLogin, never()).performLogin(eq(player), any(PlayerAuth.class));
        verify(playerCache, never()).updatePlayer(any(PlayerAuth.class));
        verify(bukkitService, never()).runTaskOptionallyAsync(any(Runnable.class));
        verifyNoLoginDialog(player);
    }

    @Test
    public void shouldReportRegisteredFlagAndSourceToListeners() {
        Player player = givenRegisteredPlayer();
        given(bungeeSender.isEnabled()).willReturn(true);
        AtomicReference<PlayerPreAuthEvent> seen = new AtomicReference<>();
        listenToPreAuth(seen::set);

        asynchronousJoin.processJoin(player);

        assertThat(seen.get().isRegistered(), is(true));
        assertThat(seen.get().getSource(), is(PlayerPreAuthEvent.Source.PROXY));
        assertThat(seen.get().isAsynchronous(), is(false));
        InOrder order = inOrder(bukkitService, limboService);
        order.verify(bukkitService).callEvent(seen.get());
        order.verify(limboService).createLimboPlayer(player, true);
    }

    @Test
    public void shouldShowPromptInSameCallWithoutAnyScheduledDelay() {
        Player player = givenRegisteredPlayer();

        asynchronousJoin.processJoin(player);

        verify(limboService).createLimboPlayer(player, true);
        verify(bukkitService, never()).runTaskLater(any(Player.class), any(Runnable.class), anyLong());
    }

    @Test
    public void shouldContinueRegularJoinWhenPreAuthEventIsNeverDispatched() {
        Player player = givenRegisteredPlayer();
        doNothing().doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(1)).run();
            return null;
        }).when(bukkitService).scheduleSyncTaskFromOptionallyAsyncTask(eq(player), any(Runnable.class));
        asynchronousJoin.setPreAuthEventTimeoutMillis(1L);

        asynchronousJoin.processJoin(player);

        verify(asynchronousLogin, never()).performLogin(any(Player.class), any(PlayerAuth.class));
        verify(limboService).createLimboPlayer(player, true);
    }

    @Test
    public void shouldLogInOnlyOnceWhenForceLoginIsRequestedTwice() {
        Player player = givenRegisteredPlayer();
        listenToPreAuth(event -> {
            event.forceLogin();
            event.forceLogin();
        });
        AtomicReference<Runnable> continuation = new AtomicReference<>();
        captureAsyncContinuation(continuation);
        given(playerCache.isAuthenticated("bobby")).willReturn(false, true);

        asynchronousJoin.processJoin(player);
        continuation.get().run();
        continuation.get().run();

        verify(asynchronousLogin, times(1)).performLogin(player, auth);
    }

    @Test
    public void shouldSkipForcedLoginWhenPlayerQuitBeforeContinuationRuns() {
        Player player = givenRegisteredPlayer();
        listenToPreAuth(PlayerPreAuthEvent::forceLogin);
        AtomicReference<Runnable> continuation = new AtomicReference<>();
        captureAsyncContinuation(continuation);
        given(player.isOnline()).willReturn(false);

        asynchronousJoin.processJoin(player);
        continuation.get().run();

        verify(asynchronousLogin, never()).performLogin(eq(player), any(PlayerAuth.class));
        verify(asynchronousLogin, never()).forceLogin(player);
        verify(limboService, never()).createLimboPlayer(eq(player), anyBoolean());
    }

    @Test
    public void shouldShowRegisterPromptForUnregisteredPlayerRequestingForceLogin() {
        Player player = mockPlayer("Bobby");
        givenDefaultJoinRestrictions(player);
        given(database.getAuth("bobby")).willReturn(null);
        given(service.getProperty(RestrictionSettings.PROTECT_INVENTORY_BEFORE_LOGIN)).willReturn(false);
        given(service.getProperty(RegistrationSettings.FORCE)).willReturn(true);
        given(service.getProperty(RestrictionSettings.REGISTER_TIMEOUT)).willReturn(30);
        given(service.getProperty(RegistrationSettings.USE_DIALOG_UI)).willReturn(false);
        given(pluginHookService.isEssentialsAvailable()).willReturn(false);
        given(service.getProperty(RegistrationSettings.APPLY_BLIND_EFFECT)).willReturn(false);
        AtomicReference<PlayerPreAuthEvent> seen = new AtomicReference<>();
        listenToPreAuth(event -> {
            seen.set(event);
            event.forceLogin();
        });

        asynchronousJoin.processJoin(player);

        assertThat(seen.get().isForceLoginRequested(), is(false));
        verify(limboService).createLimboPlayer(player, false);
        verify(asynchronousLogin, never()).performLogin(eq(player), any(PlayerAuth.class));
        verify(asynchronousLogin, never()).forceLogin(player);
        verifySingleAuthLookup("bobby");
    }

    @Test
    public void shouldContinueWithPromptWhenPreAuthListenerThrows() {
        Player player = givenRegisteredPlayer();
        doThrow(new IllegalStateException("listener failed")).when(bukkitService).callEvent(any(Event.class));

        asynchronousJoin.processJoin(player);

        verify(limboService).createLimboPlayer(player, true);
        verify(asynchronousLogin, never()).performLogin(eq(player), any(PlayerAuth.class));
        verify(asynchronousLogin, never()).forceLogin(player);
    }

    @Test
    public void shouldUseSingleLookupAndOneSyncHopForPremiumBypass() {
        Player player = givenRegisteredPlayer();
        given(service.getProperty(PremiumSettings.ENABLE_PREMIUM)).willReturn(true);
        UUID mojangUuid = UUID.randomUUID();
        given(player.getUniqueId()).willReturn(mojangUuid);
        given(auth.isPremium()).willReturn(true);
        given(auth.getPremiumUuid()).willReturn(mojangUuid);

        asynchronousJoin.processJoin(player);

        InOrder order = inOrder(bukkitService, asynchronousLogin);
        order.verify(bukkitService).callEvent(any(PlayerPreAuthEvent.class));
        order.verify(asynchronousLogin).forceLogin(player);
        verifySingleAuthLookup("bobby");
        verify(limboService, never()).createLimboPlayer(eq(player), anyBoolean());
        verify(bukkitService, times(1)).scheduleSyncTaskFromOptionallyAsyncTask(eq(player), any(Runnable.class));
    }

    private void captureAsyncContinuation(AtomicReference<Runnable> target) {
        doAnswer(invocation -> {
            target.set(invocation.getArgument(0));
            return null;
        }).when(bukkitService).runTaskOptionallyAsync(any(Runnable.class));
    }

    private void verifySingleAuthLookup(String normalizedName) {
        // The account is read once and reused; no second availability lookup is performed.
        verify(database, times(1)).getAuth(normalizedName);
        verify(database, never()).isAuthAvailable(anyString());
    }

    private void givenDialogUi(Player player) {
        given(service.getProperty(RegistrationSettings.USE_DIALOG_UI)).willReturn(true);
        given(dialogAdapter.isDialogSupported()).willReturn(true);
        given(dialogWindowService.createLoginDialog(player)).willReturn(createDialogSpec("Login", "Login"));
    }

    private void listenToPreAuth(Consumer<PlayerPreAuthEvent> listener) {
        doAnswer(invocation -> {
            Event event = invocation.getArgument(0);
            if (event instanceof PlayerPreAuthEvent) {
                listener.accept((PlayerPreAuthEvent) event);
            }
            return null;
        }).when(bukkitService).callEvent(any(Event.class));
    }

    @SuppressWarnings("unchecked")
    private void givenEventsAreCalledWithoutListeners() {
        given(bukkitService.createAndCallEvent(any())).willAnswer(invocation ->
            ((Function<Boolean, Event>) invocation.getArgument(0)).apply(false));
    }

    private void givenDefaultJoinRestrictions(Player player) {
        given(validationService.fulfillsNameRestrictions(player)).willReturn(true);
        given(service.getProperty(RestrictionSettings.UNRESTRICTED_NAMES)).willReturn(Set.of());
        given(service.getProperty(RestrictionSettings.FORCE_SURVIVAL_MODE)).willReturn(false);
        given(service.getProperty(HooksSettings.DISABLE_SOCIAL_SPY)).willReturn(false);
        given(service.getProperty(RestrictionSettings.MAX_JOIN_PER_IP)).willReturn(0);
    }

    private Player givenRegisteredPlayer() {
        Player player = mockPlayer("Bobby");
        setUpRegisteredJoin(player);
        return player;
    }

    private void verifyNoLoginDialog(Player player) {
        verify(dialogAdapter, never()).showLoginDialog(eq(player), any(DialogWindowSpec.class));
    }

    private void setUpRegisteredJoin(Player player) {
        String normalizedName = player.getName().toLowerCase();
        givenDefaultJoinRestrictions(player);
        auth = mock(PlayerAuth.class);
        given(database.getAuth(normalizedName)).willReturn(auth);
        given(service.getProperty(RestrictionSettings.PROTECT_INVENTORY_BEFORE_LOGIN)).willReturn(false);
        given(sessionService.canResumeSession(player)).willReturn(false);
        given(proxySessionManager.shouldResumeSession(normalizedName)).willReturn(false);
        given(service.getProperty(RestrictionSettings.LOGIN_TIMEOUT)).willReturn(30);
        given(service.getProperty(RegistrationSettings.USE_DIALOG_UI)).willReturn(false);
        given(pluginHookService.isEssentialsAvailable()).willReturn(false);
        given(service.getProperty(RegistrationSettings.APPLY_BLIND_EFFECT)).willReturn(false);
    }

    private static Player mockPlayer(String name) {
        Player player = mock(Player.class);
        given(player.getName()).willReturn(name);
        given(player.isOnline()).willReturn(true);
        TestHelper.mockIpAddressToPlayer(player, "127.0.0.1");
        return player;
    }

    private static DialogWindowSpec createDialogSpec(String title, String buttonLabel) {
        return new DialogWindowSpec(title,
            List.of(new DialogInputSpec("password", "Password", 100)),
            buttonLabel,
            "Cancel",
            false,
            false,
            null);
    }
}
