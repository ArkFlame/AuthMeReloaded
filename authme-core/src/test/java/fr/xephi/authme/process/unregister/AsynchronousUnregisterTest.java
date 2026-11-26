package fr.xephi.authme.process.unregister;

import fr.xephi.authme.TestHelper;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.data.auth.PlayerCache;
import fr.xephi.authme.data.limbo.LimboService;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.events.AbstractUnregisterEvent;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.security.PasswordSecurity;
import fr.xephi.authme.security.crypts.HashedPassword;
import fr.xephi.authme.service.BukkitService;
import fr.xephi.authme.service.CommonService;
import fr.xephi.authme.service.TeleportationService;
import fr.xephi.authme.service.bungeecord.BungeeSender;
import fr.xephi.authme.settings.commandconfig.CommandManager;
import fr.xephi.authme.settings.properties.RegistrationSettings;
import fr.xephi.authme.settings.properties.RestrictionSettings;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static fr.xephi.authme.service.BukkitServiceTestHelper.setBukkitServiceToScheduleSyncEntityTaskFromOptionallyAsyncTask;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Test for {@link AsynchronousUnregister}.
 */
@ExtendWith(MockitoExtension.class)
class AsynchronousUnregisterTest {

    @InjectMocks
    private AsynchronousUnregister asynchronousUnregister;

    @Mock
    private DataSource dataSource;
    @Mock
    private CommonService service;
    @Mock
    private PasswordSecurity passwordSecurity;
    @Mock
    private PlayerCache playerCache;
    @Mock
    private BukkitService bukkitService;
    @Mock
    private LimboService limboService;
    @Mock
    private TeleportationService teleportationService;
    @Mock
    private CommandManager commandManager;
    @Mock
    private BungeeSender bungeeSender;
    @Mock
    private Player player;
    @Mock
    private CommandSender initiator;

    @BeforeAll
    static void initLogger() {
        TestHelper.setupLogger();
    }

    @Test
    void shouldRejectWrongPassword() {
        String name = "Bobby";
        HashedPassword password = givenRegisteredPlayer(player, name);
        given(passwordSecurity.comparePassword("pass", password, name)).willReturn(false);

        asynchronousUnregister.unregister(player, "pass");

        verify(service).send(player, MessageKey.WRONG_PASSWORD);
        verify(passwordSecurity).comparePassword("pass", password, name);
        verifyNoInteractions(dataSource, limboService, teleportationService, bukkitService, bungeeSender);
        verify(player, only()).getName();
    }

    @Test
    void shouldDoNothingIfPlayerIsNoLongerLoggedIn() {
        given(player.getName()).willReturn("Bobby");
        given(playerCache.getAuth("Bobby")).willReturn(null);

        asynchronousUnregister.unregister(player, "pass");

        verifyNoInteractions(passwordSecurity, dataSource, service);
    }

    @ParameterizedTest(name = "blind effect={0}")
    @ValueSource(booleans = {true, false})
    void shouldPerformUnregister(boolean applyBlindEffect) {
        String name = "Frank21";
        HashedPassword password = givenRegisteredPlayer(player, name);
        given(player.isOnline()).willReturn(true);
        given(passwordSecurity.comparePassword("pass", password, name)).willReturn(true);
        given(dataSource.removeAuth(name)).willReturn(true);
        given(service.getProperty(RegistrationSettings.FORCE)).willReturn(true);
        given(service.getProperty(RegistrationSettings.APPLY_BLIND_EFFECT)).willReturn(applyBlindEffect);
        setBukkitServiceToScheduleSyncEntityTaskFromOptionallyAsyncTask(bukkitService);
        if (applyBlindEffect) {
            given(service.getProperty(RestrictionSettings.REGISTER_TIMEOUT)).willReturn(21);
            given(bukkitService.createBlindnessEffect(21 * 20)).willReturn(mock(PotionEffect.class));
        }

        asynchronousUnregister.unregister(player, "pass");

        verifySuccessfulUnregister(player, name);
        verify(passwordSecurity).comparePassword("pass", password, name);
        verify(teleportationService).teleportOnJoin(player);
        if (!applyBlindEffect) {
            verify(player, never()).addPotionEffect(any(PotionEffect.class));
        }
    }

    @Test
    void shouldOnlyTeleportOnThePlayerThread() {
        String name = "Frank21";
        HashedPassword password = givenRegisteredPlayer(player, name);
        given(player.isOnline()).willReturn(true);
        given(passwordSecurity.comparePassword("pass", password, name)).willReturn(true);
        given(dataSource.removeAuth(name)).willReturn(true);
        given(service.getProperty(RegistrationSettings.FORCE)).willReturn(true);
        given(service.getProperty(RegistrationSettings.APPLY_BLIND_EFFECT)).willReturn(false);

        List<Runnable> scheduledTasks = new ArrayList<>();
        doAnswer(invocation -> {
            scheduledTasks.add(invocation.getArgument(1));
            return null;
        }).when(bukkitService).scheduleSyncTaskFromOptionallyAsyncTask(any(Player.class), any(Runnable.class));

        asynchronousUnregister.unregister(player, "pass");

        verify(teleportationService, never()).teleportOnJoin(player);
        assertThat(scheduledTasks.size(), equalTo(1));
        scheduledTasks.get(0).run();
        verify(teleportationService).teleportOnJoin(player);
        verify(commandManager).runCommandsOnUnregister(player);
    }

    @Test
    void shouldNotApplyUnregisteredEffectsForNotForcedRegistration() {
        String name = "__FranK";
        given(player.getName()).willReturn(name);
        given(player.isOnline()).willReturn(true);
        String userPassword = "141$$5ad";
        HashedPassword password = new HashedPassword("ttt123");
        PlayerAuth auth = PlayerAuth.builder().name(name).password(password).build();
        given(playerCache.getAuth(name)).willReturn(auth);
        given(passwordSecurity.comparePassword(userPassword, password, name)).willReturn(true);
        given(dataSource.removeAuth(name)).willReturn(true);
        given(service.getProperty(RegistrationSettings.FORCE)).willReturn(false);
        setBukkitServiceToScheduleSyncEntityTaskFromOptionallyAsyncTask(bukkitService);

        asynchronousUnregister.unregister(player, userPassword);

        verifySuccessfulUnregister(player, name);
        verify(passwordSecurity).comparePassword(userPassword, password, name);
        verifyNoInteractions(teleportationService, limboService);
    }

    @Test
    void shouldHandleDatabaseError() {
        String name = "Frank21";
        HashedPassword password = givenRegisteredPlayer(player, name);
        given(passwordSecurity.comparePassword("pass", password, name)).willReturn(true);
        given(dataSource.removeAuth(name)).willReturn(false);

        asynchronousUnregister.unregister(player, "pass");

        verify(passwordSecurity).comparePassword("pass", password, name);
        verify(dataSource).removeAuth(name);
        verify(service).send(player, MessageKey.ERROR);
        verifyNoInteractions(teleportationService, bukkitService, bungeeSender);
    }

    @Test
    void shouldNotTeleportOfflinePlayer() {
        String name = "Frank21";
        HashedPassword password = givenRegisteredPlayer(player, name);
        given(player.isOnline()).willReturn(false);
        given(passwordSecurity.comparePassword("pass", password, name)).willReturn(true);
        given(dataSource.removeAuth(name)).willReturn(true);

        asynchronousUnregister.unregister(player, "pass");

        verify(passwordSecurity).comparePassword("pass", password, name);
        verify(dataSource).removeAuth(name);
        verify(playerCache).removePlayer(name);
        verifyNoInteractions(teleportationService);
        verifyCalledUnregisterEventFor(player);
    }

    // Initiator known and Player object available
    @Test
    void shouldPerformAdminUnregister() {
        String name = "Frank21";
        given(player.isOnline()).willReturn(true);
        given(dataSource.removeAuth(name)).willReturn(true);
        given(service.getProperty(RegistrationSettings.FORCE)).willReturn(true);
        given(service.getProperty(RegistrationSettings.APPLY_BLIND_EFFECT)).willReturn(false);
        setBukkitServiceToScheduleSyncEntityTaskFromOptionallyAsyncTask(bukkitService);

        asynchronousUnregister.adminUnregister(initiator, name, player);

        verify(service).send(initiator, MessageKey.UNREGISTERED_SUCCESS);
        verifySuccessfulUnregister(player, name);
        verify(teleportationService).teleportOnJoin(player);
    }

    @Test
    void shouldPerformAdminUnregisterWithoutInitiatorOrPlayer() {
        String name = "billy";
        given(dataSource.removeAuth(name)).willReturn(true);

        asynchronousUnregister.adminUnregister(null, name, null);

        verify(dataSource).removeAuth(name);
        verify(playerCache).removePlayer(name);
        verifyNoInteractions(teleportationService);
        verifyCalledUnregisterEventFor(null);
    }

    @Test
    void shouldHandleDatabaseErrorForAdminUnregister() {
        String name = "TtOoLl";
        given(dataSource.removeAuth(name)).willReturn(false);

        asynchronousUnregister.adminUnregister(initiator, name, null);

        verify(dataSource).removeAuth(name);
        verify(service).send(initiator, MessageKey.ERROR);
        verifyNoInteractions(playerCache, teleportationService, bukkitService, bungeeSender);
    }

    private void verifySuccessfulUnregister(Player player, String name) {
        verify(service).send(player, MessageKey.UNREGISTERED_SUCCESS);
        verify(dataSource).removeAuth(name);
        verify(playerCache).removePlayer(name);
        verifyCalledUnregisterEventFor(player);
        verify(commandManager).runCommandsOnUnregister(player);
    }

    private HashedPassword givenRegisteredPlayer(Player player, String name) {
        given(player.getName()).willReturn(name);
        PlayerAuth auth = mock(PlayerAuth.class);
        given(playerCache.getAuth(name)).willReturn(auth);
        HashedPassword password = new HashedPassword("password", "in_auth_obj");
        given(auth.getPassword()).willReturn(password);
        return password;
    }

    @SuppressWarnings("unchecked")
    private void verifyCalledUnregisterEventFor(Player player) {
        ArgumentCaptor<Function<Boolean, AbstractUnregisterEvent>> eventFunctionCaptor =
            ArgumentCaptor.forClass(Function.class);
        verify(bukkitService).createAndCallEvent(eventFunctionCaptor.capture());
        AbstractUnregisterEvent event = eventFunctionCaptor.getValue().apply(true);
        assertThat(event.getPlayer(), equalTo(player));
    }
}
