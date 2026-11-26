package fr.xephi.authme.listener;

import fr.xephi.authme.data.QuickCommandsProtectionManager;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.data.auth.PlayerCache;
import fr.xephi.authme.data.limbo.LimboService;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.message.Messages;
import fr.xephi.authme.permission.PermissionsManager;
import fr.xephi.authme.permission.PlayerStatePermission;
import fr.xephi.authme.platform.ChatAdapter;
import fr.xephi.authme.platform.TeleportAdapter;
import fr.xephi.authme.process.Management;
import fr.xephi.authme.service.BukkitService;
import fr.xephi.authme.service.JoinMessageService;
import fr.xephi.authme.service.TeleportationService;
import fr.xephi.authme.service.ValidationService;
import fr.xephi.authme.settings.Settings;
import fr.xephi.authme.settings.SpawnLoader;
import fr.xephi.authme.settings.properties.HooksSettings;
import fr.xephi.authme.settings.properties.RegistrationSettings;
import fr.xephi.authme.settings.properties.RestrictionSettings;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.InventoryView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockMakers;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.google.common.collect.Sets.newHashSet;
import static fr.xephi.authme.listener.EventCancelVerifier.withServiceMock;
import static fr.xephi.authme.listener.TestPlayerUtils.createInetAddress;
import static fr.xephi.authme.listener.TestPlayerUtils.mockPlayerWithName;
import static fr.xephi.authme.service.BukkitServiceTestHelper.setBukkitServiceToScheduleSyncDelayedTaskWithEntityAndDelay;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Test for {@link PlayerListener}.
 */
@ExtendWith(MockitoExtension.class)
class PlayerListenerTest {

    private static final String TEST_PLAYER_NAME = "someone";
    private static final UUID TEST_PLAYER_UUID = UUID.fromString("753493c9-33ba-4a4a-bf61-1bce9d3c9a71");

    @InjectMocks
    private PlayerListener listener;

    @Mock(mockMaker = MockMakers.INLINE)
    private Settings settings;
    @Mock(mockMaker = MockMakers.INLINE)
    private Messages messages;
    @Mock(mockMaker = MockMakers.INLINE)
    private DataSource dataSource;
    @Mock(mockMaker = MockMakers.INLINE)
    private Management management;
    @Mock(mockMaker = MockMakers.INLINE)
    private BukkitService bukkitService;
    @Mock(mockMaker = MockMakers.INLINE)
    private SpawnLoader spawnLoader;
    @Mock(mockMaker = MockMakers.INLINE)
    private OnJoinVerifier onJoinVerifier;
    @Mock(mockMaker = MockMakers.INLINE)
    private ListenerService listenerService;
    @Mock(mockMaker = MockMakers.INLINE)
    private TeleportationService teleportationService;
    @Mock(mockMaker = MockMakers.INLINE)
    private ValidationService validationService;
    @Mock(mockMaker = MockMakers.INLINE)
    private JoinMessageService joinMessageService;
    @Mock(mockMaker = MockMakers.INLINE)
    private QuickCommandsProtectionManager quickCommandsProtectionManager;
    @Mock(mockMaker = MockMakers.INLINE)
    private PermissionsManager permissionsManager;
    @Mock(mockMaker = MockMakers.INLINE)
    private LimboService limboService;
    @Mock(mockMaker = MockMakers.INLINE)
    private ChatAdapter chatAdapter;
    @Mock(mockMaker = MockMakers.INLINE)
    private TeleportAdapter teleportAdapter;
    @Mock(mockMaker = MockMakers.INLINE)
    private PlayerCache playerCache;

    @AfterEach
    void resetSpawnLocationTracker() throws ReflectiveOperationException {
        Field eventCalledField = SpawnLocationTracker.class.getDeclaredField("eventCalled");
        eventCalledField.setAccessible(true);
        eventCalledField.setBoolean(null, false);
    }

    /**
     * #831: If a player is kicked because of "logged in from another location", the kick
     * should be CANCELED when single session is enabled.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("kickCases")
    void shouldHandleKick(String description, boolean forceSingleSession, String reason,
                          boolean stubKickReason, boolean expectedCancelled) {
        given(settings.getProperty(RestrictionSettings.FORCE_SINGLE_SESSION)).willReturn(forceSingleSession);
        if (stubKickReason) {
            given(chatAdapter.getKickReason(any()))
                .willAnswer(inv -> inv.getArgument(0, PlayerKickEvent.class).getReason());
        }
        Player player = mock(Player.class, withSettings().mockMaker(MockMakers.INLINE));
        PlayerKickEvent event = new PlayerKickEvent(player, reason, "");

        listener.onPlayerKick(event);

        assertThat(event.isCancelled(), equalTo(expectedCancelled));
        verifyNoInteractions(player, management);
    }

    private static Stream<Arguments> kickCases() {
        return Stream.of(
            Arguments.of("cancel duplicate login", true, "You logged in from another location", true, true),
            Arguments.of("single session disabled", false, "You logged in from another location", false, false),
            Arguments.of("ordinary kick", true, "No longer desired here!", true, false));
    }

    @Test
    void shouldHandleSimpleCancelableEvents() {
        withServiceMock(listenerService)
            .check(listener::onPlayerShear, PlayerShearEntityEvent.class)
            .check(listener::onPlayerFish, PlayerFishEvent.class)
            .check(listener::onPlayerBedEnter, PlayerBedEnterEvent.class)
            .check(listener::onPlayerDropItem, PlayerDropItemEvent.class)
            .check(listener::onPlayerHitPlayerEvent, EntityDamageByEntityEvent.class)
            .check(listener::onPlayerConsumeItem, PlayerItemConsumeEvent.class)
            .check(listener::onPlayerInteract, PlayerInteractEvent.class)
            .check(listener::onPlayerInteractEntity, PlayerInteractEntityEvent.class)
            .check(listener::onPlayerHeldItem, PlayerItemHeldEvent.class)
            .check(listener::onPlayerPortal, PlayerPortalEvent.class)
            .check(listener::onPlayerEditBook, PlayerEditBookEvent.class)
            .check(listener::onPlayerInteractAtEntity, PlayerInteractAtEntityEvent.class);
    }

    @Test
    void shouldCancelEnderPearlTeleportOfUnauthenticatedPlayer() {
        PlayerTeleportEvent event = mock(PlayerTeleportEvent.class, withSettings().mockMaker(MockMakers.INLINE));
        given(event.getCause()).willReturn(PlayerTeleportEvent.TeleportCause.ENDER_PEARL);
        given(listenerService.shouldCancelEvent(event)).willReturn(true);

        listener.onPlayerTeleport(event);

        verify(event).setCancelled(true);
    }

    @Test
    void shouldNotCancelPluginTeleport() {
        PlayerTeleportEvent event = mock(PlayerTeleportEvent.class, withSettings().mockMaker(MockMakers.INLINE));
        given(event.getCause()).willReturn(PlayerTeleportEvent.TeleportCause.PLUGIN);

        listener.onPlayerTeleport(event);

        verify(event, never()).setCancelled(anyBoolean());
        verifyNoInteractions(listenerService);
    }

    @Test
    void shouldAllowEssentialsMotd() {
        given(settings.getProperty(HooksSettings.USE_ESSENTIALS_MOTD)).willReturn(true);
        PlayerCommandPreprocessEvent event = mockCommandEvent("/MOTD");

        listener.onPlayerCommandPreprocess(event);

        verify(event, only()).getMessage();
        verifyNoInteractions(listenerService, messages);
    }

    @Test
    void shouldNotStopAllowedCommand() {
        given(settings.getProperty(HooksSettings.USE_ESSENTIALS_MOTD)).willReturn(true);
        given(settings.getProperty(RestrictionSettings.ALLOW_COMMANDS)).willReturn(newHashSet("/plugins", "/mail", "/msg"));
        PlayerCommandPreprocessEvent event = mockCommandEvent("/Mail send test Test");

        listener.onPlayerCommandPreprocess(event);

        verify(event, only()).getMessage();
        verifyNoInteractions(listenerService, messages);
    }

    @Test
    void shouldNotCancelEventForAuthenticatedPlayer() {
        given(settings.getProperty(HooksSettings.USE_ESSENTIALS_MOTD)).willReturn(false);
        given(settings.getProperty(RestrictionSettings.ALLOW_COMMANDS)).willReturn(Collections.emptySet());
        Player player = playerWithMockedServer();
        // PlayerCommandPreprocessEvent#getPlayer is final, so create a spy instead of a mock
        PlayerCommandPreprocessEvent event = inlineSpy(new PlayerCommandPreprocessEvent(player, "/hub"));
        given(listenerService.shouldCancelEvent(player)).willReturn(false);
        given(quickCommandsProtectionManager.isAllowed(player.getName())).willReturn(true);

        listener.onPlayerCommandPreprocess(event);

        verify(event).getMessage();
        verify(event, atLeast(0)).getPlayer();
        verifyNoMoreInteractions(event);
        verify(listenerService).shouldCancelEvent(player);
        verifyNoInteractions(messages);
    }

    @Test
    void shouldCancelCommandEvent() {
        given(settings.getProperty(HooksSettings.USE_ESSENTIALS_MOTD)).willReturn(false);
        given(settings.getProperty(RestrictionSettings.ALLOW_COMMANDS)).willReturn(newHashSet("/spawn", "/help"));
        Player player = playerWithMockedServer();
        PlayerCommandPreprocessEvent event = inlineSpy(new PlayerCommandPreprocessEvent(player, "/hub"));
        given(listenerService.shouldCancelEvent(player)).willReturn(true);
        given(quickCommandsProtectionManager.isAllowed(player.getName())).willReturn(true);

        listener.onPlayerCommandPreprocess(event);

        verify(listenerService).shouldCancelEvent(player);
        verify(event).setCancelled(true);
        verify(messages).send(player, MessageKey.DENIED_COMMAND);
    }

    @Test
    void shouldCancelFastCommandEvent() {
        given(settings.getProperty(HooksSettings.USE_ESSENTIALS_MOTD)).willReturn(false);
        given(settings.getProperty(RestrictionSettings.ALLOW_COMMANDS)).willReturn(newHashSet("/spawn", "/help"));
        Player player = playerWithMockedServer();
        PlayerCommandPreprocessEvent event = inlineSpy(new PlayerCommandPreprocessEvent(player, "/hub"));
        given(quickCommandsProtectionManager.isAllowed(player.getName())).willReturn(false);

        listener.onPlayerCommandPreprocess(event);

        verify(event).setCancelled(true);
        verify(player).kickPlayer(messages.retrieveSingle(player, MessageKey.QUICK_COMMAND_PROTECTION_KICK));
    }

    @Test
    void shouldAllowChat() {
        given(settings.getProperty(RestrictionSettings.ALLOW_CHAT)).willReturn(true);
        AsyncPlayerChatEvent event = mock(AsyncPlayerChatEvent.class, withSettings().mockMaker(MockMakers.INLINE));

        listener.onPlayerChat(event);

        verifyNoInteractions(event, listenerService, messages);
    }

    @Test
    void shouldCancelChatForUnauthedPlayer() {
        given(settings.getProperty(RestrictionSettings.ALLOW_CHAT)).willReturn(false);
        AsyncPlayerChatEvent event = newAsyncChatEvent();
        given(listenerService.shouldCancelEvent(event.getPlayer())).willReturn(true);
        given(permissionsManager.hasPermission(event.getPlayer(), PlayerStatePermission.ALLOW_CHAT_BEFORE_LOGIN)).willReturn(false);

        listener.onPlayerChat(event);

        verify(listenerService).shouldCancelEvent(event.getPlayer());
        verify(permissionsManager).hasPermission(event.getPlayer(), PlayerStatePermission.ALLOW_CHAT_BEFORE_LOGIN);
        verify(event).setCancelled(true);
        verify(messages).send(event.getPlayer(), MessageKey.DENIED_CHAT);
    }

    @Test
    void shouldSendChatToEveryone() {
        given(settings.getProperty(RestrictionSettings.ALLOW_CHAT)).willReturn(false);
        AsyncPlayerChatEvent event = newAsyncChatEvent();
        given(listenerService.shouldCancelEvent(event.getPlayer())).willReturn(false);
        given(settings.getProperty(RestrictionSettings.HIDE_CHAT)).willReturn(false);

        listener.onPlayerChat(event);

        verify(listenerService).shouldCancelEvent(event.getPlayer());
        verify(event, atLeast(0)).getPlayer();
        verifyNoMoreInteractions(event);
        verifyNoInteractions(messages);
    }

    @Test
    void shouldHideChatFromUnauthed() {
        given(settings.getProperty(RestrictionSettings.ALLOW_CHAT)).willReturn(false);
        AsyncPlayerChatEvent event = newAsyncChatEvent();
        given(listenerService.shouldCancelEvent(event.getPlayer())).willReturn(false);
        given(settings.getProperty(RestrictionSettings.HIDE_CHAT)).willReturn(true);
        List<Player> recipients = new ArrayList<>(event.getRecipients());
        given(listenerService.shouldCancelEvent(recipients.get(0))).willReturn(true);

        listener.onPlayerChat(event);

        verify(listenerService).shouldCancelEvent(event.getPlayer());
        // message sender + 3 recipients = 4
        verify(listenerService, times(4)).shouldCancelEvent(any(Player.class));
        verify(event, never()).setCancelled(anyBoolean());
        assertThat(event.getRecipients(), containsInAnyOrder(recipients.get(1), recipients.get(2)));
    }

    @Test
    void shouldCancelChatEventForNoRemainingRecipients() {
        given(settings.getProperty(RestrictionSettings.ALLOW_CHAT)).willReturn(false);
        AsyncPlayerChatEvent event = newAsyncChatEvent();
        given(listenerService.shouldCancelEvent(any(Player.class))).willReturn(true);
        given(listenerService.shouldCancelEvent(event.getPlayer())).willReturn(false);
        given(settings.getProperty(RestrictionSettings.HIDE_CHAT)).willReturn(true);

        listener.onPlayerChat(event);

        verify(listenerService).shouldCancelEvent(event.getPlayer());
        // message sender + 3 recipients = 4
        verify(listenerService, times(4)).shouldCancelEvent(any(Player.class));
        verify(event).setCancelled(true);
        assertThat(event.getRecipients(), empty());
    }

    @Test
    void shouldAllowChatForBypassPermission() {
        given(settings.getProperty(RestrictionSettings.ALLOW_CHAT)).willReturn(false);
        AsyncPlayerChatEvent event = newAsyncChatEvent();
        given(listenerService.shouldCancelEvent(event.getPlayer())).willReturn(true);
        given(permissionsManager.hasPermission(event.getPlayer(), PlayerStatePermission.ALLOW_CHAT_BEFORE_LOGIN)).willReturn(true);
        given(settings.getProperty(RestrictionSettings.HIDE_CHAT)).willReturn(false);

        listener.onPlayerChat(event);

        assertThat(event.isCancelled(), equalTo(false));
        verify(listenerService).shouldCancelEvent(event.getPlayer());
        verify(permissionsManager).hasPermission(event.getPlayer(), PlayerStatePermission.ALLOW_CHAT_BEFORE_LOGIN);
        assertThat(event.getRecipients(), hasSize(3));
    }

    @Test
    void shouldAllowUnlimitedMovement() {
        given(settings.getProperty(RestrictionSettings.ALLOW_UNAUTHED_MOVEMENT)).willReturn(true);
        given(settings.getProperty(RestrictionSettings.ALLOWED_MOVEMENT_RADIUS)).willReturn(0);
        Player player = mock(Player.class, withSettings().mockMaker(MockMakers.INLINE));
        Location location = mock(Location.class, withSettings().mockMaker(MockMakers.INLINE));
        PlayerMoveEvent event = inlineSpy(new PlayerMoveEvent(player, location, location));

        listener.onPlayerMove(event);

        verifyNoInteractions(event);
    }

    @Test
    void shouldAllowFalling() {
        given(settings.getProperty(RestrictionSettings.ALLOW_UNAUTHED_MOVEMENT)).willReturn(false);
        Player player = mock(Player.class, withSettings().mockMaker(MockMakers.INLINE));
        Location from = new Location(null, 100, 90, 200);
        Location to = new Location(null, 100, 88, 200);
        PlayerMoveEvent event = inlineSpy(new PlayerMoveEvent(player, from, to));

        listener.onPlayerMove(event);

        verifyNoModifyingCalls(event);
    }

    @Test
    void shouldAllowMovementForAuthedPlayer() {
        given(settings.getProperty(RestrictionSettings.ALLOW_UNAUTHED_MOVEMENT)).willReturn(false);
        Player player = mock(Player.class, withSettings().mockMaker(MockMakers.INLINE));
        Location from = new Location(null, 100, 90, 200);
        Location to = new Location(null, 99, 90, 200);
        PlayerMoveEvent event = inlineSpy(new PlayerMoveEvent(player, from, to));
        given(listenerService.shouldCancelEvent(player)).willReturn(false);

        listener.onPlayerMove(event);

        verify(listenerService).shouldCancelEvent(player);
        verifyNoModifyingCalls(event);
    }

    @Test
    void shouldCancelEventForDisabledUnauthedMovement() {
        given(settings.getProperty(RestrictionSettings.ALLOW_UNAUTHED_MOVEMENT)).willReturn(false);
        Player player = mock(Player.class, withSettings().mockMaker(MockMakers.INLINE));
        World world = mock(World.class, withSettings().mockMaker(MockMakers.INLINE));
        Location from = new Location(world, 200, 70, 200);
        Location to = new Location(world, 199, 70, 199);
        PlayerMoveEvent event = inlineSpy(new PlayerMoveEvent(player, from, to));
        given(listenerService.shouldCancelEvent(player)).willReturn(true);

        listener.onPlayerMove(event);

        verify(listenerService).shouldCancelEvent(player);
        verify(event).setTo(from);
    }

    @Test
    void shouldTeleportPlayerInDifferentWorldToSpawn() {
        given(settings.getProperty(RestrictionSettings.ALLOW_UNAUTHED_MOVEMENT)).willReturn(true);
        given(settings.getProperty(RestrictionSettings.ALLOWED_MOVEMENT_RADIUS)).willReturn(20);
        World playerWorld = mock(World.class, withSettings().mockMaker(MockMakers.INLINE));
        Player player = mock(Player.class, withSettings().mockMaker(MockMakers.INLINE));
        given(player.getWorld()).willReturn(playerWorld);
        Location from = new Location(null, 200, 70, 200);
        Location to = new Location(null, 199, 70, 199);
        PlayerMoveEvent event = inlineSpy(new PlayerMoveEvent(player, from, to));
        given(listenerService.shouldCancelEvent(player)).willReturn(true);
        given(settings.getProperty(RestrictionSettings.NO_TELEPORT)).willReturn(false);
        World world = mock(World.class, withSettings().mockMaker(MockMakers.INLINE));
        Location spawn = new Location(world, 0, 90, 0);
        given(spawnLoader.getSpawnLocation(player)).willReturn(spawn);

        listener.onPlayerMove(event);

        verify(listenerService).shouldCancelEvent(player);
        verify(teleportAdapter).teleportPlayer(player, spawn);
        verify(spawnLoader).getSpawnLocation(player);
        verifyNoModifyingCalls(event);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("movementRadiusCases")
    void shouldEnforceMovementRadius(String description, boolean withinRadius) {
        given(settings.getProperty(RestrictionSettings.ALLOW_UNAUTHED_MOVEMENT)).willReturn(true);
        given(settings.getProperty(RestrictionSettings.ALLOWED_MOVEMENT_RADIUS)).willReturn(12);
        World world = mock(World.class, withSettings().mockMaker(MockMakers.INLINE));
        Player player = mock(Player.class, withSettings().mockMaker(MockMakers.INLINE));
        given(player.getWorld()).willReturn(world);
        Location from = new Location(world, 200, 70, 200);
        Location to = new Location(world, 199, 69, 201);
        PlayerMoveEvent event = inlineSpy(new PlayerMoveEvent(player, from, to));
        given(player.getLocation()).willReturn(from);
        given(listenerService.shouldCancelEvent(player)).willReturn(true);
        given(settings.getProperty(RestrictionSettings.NO_TELEPORT)).willReturn(false);
        // Distance from the spawn: sqrt(10^2 + 2^2 + 4^2) = 11 (allowed), sqrt(15^2 + 2^2 + 4^2) = 16 (rejected)
        Location spawn = new Location(world, withinRadius ? 190 : 185, 72, 204);
        given(spawnLoader.getSpawnLocation(player)).willReturn(spawn);

        listener.onPlayerMove(event);

        verify(listenerService).shouldCancelEvent(player);
        if (withinRadius) {
            verify(player, never()).teleport(any(Location.class));
        } else {
            verify(teleportAdapter).teleportPlayer(player, spawn);
        }
        verify(spawnLoader).getSpawnLocation(player);
        verifyNoModifyingCalls(event);
    }

    private static Stream<Arguments> movementRadiusCases() {
        return Stream.of(
            Arguments.of("movement within radius", true),
            Arguments.of("movement outside radius", false));
    }

    @Test
    void shouldIgnorePlayerRespawnForAuthenticatedPlayer() {
        Player player = mock(Player.class, withSettings().mockMaker(MockMakers.INLINE));
        Location respawnLocation = mock(Location.class, withSettings().mockMaker(MockMakers.INLINE));
        PlayerRespawnEvent event = inlineSpy(new PlayerRespawnEvent(player, respawnLocation, false));
        given(listenerService.shouldCancelEvent(event)).willReturn(false);

        listener.onPlayerRespawn(event);

        verifyNoInteractions(spawnLoader, bukkitService);
        verify(event, never()).setRespawnLocation(any());
    }

    @Test
    void shouldSkipTeleportOnRespawnWithNoTeleportButStillScheduleRestrictions() {
        Player player = mock(Player.class, withSettings().mockMaker(MockMakers.INLINE));
        Location respawnLocation = mock(Location.class, withSettings().mockMaker(MockMakers.INLINE));
        PlayerRespawnEvent event = inlineSpy(new PlayerRespawnEvent(player, respawnLocation, false));
        given(listenerService.shouldCancelEvent(event)).willReturn(true);
        given(settings.getProperty(RestrictionSettings.NO_TELEPORT)).willReturn(true);

        listener.onPlayerRespawn(event);

        verifyNoInteractions(spawnLoader);
        verify(event, never()).setRespawnLocation(any());
        verify(bukkitService).runTaskLater(eq(player), any(Runnable.class), eq(1L));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("respawnLocations")
    void shouldApplyRespawnLocation(String description, Location respawnLocation, boolean expectLocationChange) {
        Player player = mock(Player.class, withSettings().mockMaker(MockMakers.INLINE));
        Location originalLocation = mock(Location.class, withSettings().mockMaker(MockMakers.INLINE));
        PlayerRespawnEvent event = inlineSpy(new PlayerRespawnEvent(player, originalLocation, false));
        given(settings.getProperty(RestrictionSettings.NO_TELEPORT)).willReturn(false);
        given(listenerService.shouldCancelEvent(event)).willReturn(true);
        given(spawnLoader.getPlayerRespawnLocationOrSpawn(player)).willReturn(respawnLocation);

        listener.onPlayerRespawn(event);

        verify(spawnLoader).getPlayerRespawnLocationOrSpawn(player);
        if (expectLocationChange) {
            verify(event).setRespawnLocation(respawnLocation);
        } else {
            verify(event, never()).setRespawnLocation(any());
        }
        verify(bukkitService).runTaskLater(eq(player), any(Runnable.class), eq(1L));
    }

    /** Location only weakly references its world, so keep a strong reference for the test's lifetime. */
    private static final World LOADED_WORLD = mock(World.class, withSettings().mockMaker(MockMakers.INLINE));

    private static Stream<Arguments> respawnLocations() {
        return Stream.of(
            Arguments.of("spawn with loaded world", new Location(LOADED_WORLD, 0, 90, 0), true),
            Arguments.of("spawn with unloaded world", new Location(null, 0, 90, 0), false),
            Arguments.of("no spawn location", null, false));
    }

    @Test
    void shouldHandlePlayerJoining() {
        Player player = mock(Player.class, withSettings().mockMaker(MockMakers.INLINE));
        PlayerJoinEvent event = new PlayerJoinEvent(player, "join message");

        listener.onPlayerJoin(event);

        verify(teleportationService).teleportNewPlayerToFirstSpawn(player);
        verify(management).performJoin(player);
    }

    @Test
    void shouldDiscardCachedAuthenticationOnJoin() {
        Player player = mock(Player.class, withSettings().mockMaker(MockMakers.INLINE));
        given(player.getName()).willReturn("Bobby");
        PlayerJoinEvent event = new PlayerJoinEvent(player, "join message");

        listener.onPlayerJoinLowest(event);

        verify(playerCache).removePlayer("Bobby");
    }

    @ParameterizedTest(name = "unrestricted player: {0}")
    @ValueSource(booleans = {false, true})
    void shouldRevokeOperatorOnJoinUnlessPlayerIsUnrestricted(boolean unrestricted) {
        Player player = mockPlayerWithName("Bobby");
        given(validationService.isUnrestricted("Bobby")).willReturn(unrestricted);
        PlayerJoinEvent event = new PlayerJoinEvent(player, "join message");

        listener.onPlayerJoinLowest(event);

        if (unrestricted) {
            verify(limboService, never()).revokeOperatorOnJoin(player);
        } else {
            verify(limboService).revokeOperatorOnJoin(player);
        }
    }

    @Test
    void shouldPerformAllJoinVerificationsSuccessfullyPreLoginLowest() throws FailedVerificationException {
        AsyncPlayerPreLoginEvent preLoginEvent = newPreLoginEvent("12.34.56.78");
        given(validationService.isUnrestricted(TEST_PLAYER_NAME)).willReturn(false);

        listener.onAsyncPlayerPreLoginEventLowest(preLoginEvent);

        verify(validationService).isUnrestricted(TEST_PLAYER_NAME);
        verify(onJoinVerifier).checkIsValidName(TEST_PLAYER_NAME, TEST_PLAYER_UUID);
        verifyNoInteractions(dataSource);
        verifyNoModifyingCalls(preLoginEvent);
    }

    @Test
    void shouldKickPreLoginLowestUnresolvedHostname() {
        String name = TEST_PLAYER_NAME;
        UUID uniqueId = TEST_PLAYER_UUID;
    
        @SuppressWarnings("ConstantConditions")
        AsyncPlayerPreLoginEvent preLoginEvent = inlineSpy(new AsyncPlayerPreLoginEvent(name, null, uniqueId));
        given(messages.retrieveSingle(name, MessageKey.KICK_UNRESOLVED_HOSTNAME)).willReturn("Unresolved hostname");

        listener.onAsyncPlayerPreLoginEventLowest(preLoginEvent);

        verify(preLoginEvent).disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, "Unresolved hostname");
        verifyNoMoreInteractions(onJoinVerifier);
    }

    @Test
    void shouldPerformAllJoinVerificationsSuccessfullyPreLoginHighest() throws FailedVerificationException {
        AsyncPlayerPreLoginEvent preLoginEvent = newPreLoginEvent("12.34.56.78");
        given(validationService.isUnrestricted(TEST_PLAYER_NAME)).willReturn(false);
        PlayerAuth auth = PlayerAuth.builder().name(TEST_PLAYER_NAME).build();
        given(dataSource.getAuth(TEST_PLAYER_NAME)).willReturn(auth);

        listener.onAsyncPlayerPreLoginEventHighest(preLoginEvent);

        verify(validationService).isUnrestricted(TEST_PLAYER_NAME);
        verify(onJoinVerifier).checkNameRestrictions(eq(TEST_PLAYER_NAME), any(InetAddress.class));
        verify(onJoinVerifier).checkKickNonRegistered(true);
        verify(onJoinVerifier).checkAntibot(TEST_PLAYER_NAME, true);
        verify(onJoinVerifier).checkNameCasing(TEST_PLAYER_NAME, auth);
        verify(onJoinVerifier).checkPlayerCountry(TEST_PLAYER_NAME, "12.34.56.78", true);
        verify(playerCache).removePlayer(TEST_PLAYER_NAME);
        verifyNoModifyingCalls(preLoginEvent);
    }

    @Test
    void shouldKeepCachedLoginOnPreLoginWhenPlayerWithNameIsOnline() {
        AsyncPlayerPreLoginEvent preLoginEvent = newPreLoginEvent("12.34.56.78");
        given(validationService.isUnrestricted(TEST_PLAYER_NAME)).willReturn(false);
        given(bukkitService.getPlayerExact(TEST_PLAYER_NAME)).willReturn(mock(Player.class, withSettings().mockMaker(MockMakers.INLINE)));

        listener.onAsyncPlayerPreLoginEventHighest(preLoginEvent);

        verify(playerCache, never()).removePlayer(anyString());
    }

    @Test
    void shouldAbortPreLoginHighestForFailedNameRestriction() throws FailedVerificationException {
        String name = "restrictedPlayer";
        UUID uniqueId = UUID.fromString("c60b7305-3a78-4f71-8a60-70a3f17e1c90");
        InetAddress address = createInetAddress("10.20.30.40");
        AsyncPlayerPreLoginEvent event = inlineSpy(new AsyncPlayerPreLoginEvent(name, address, uniqueId));
        given(validationService.isUnrestricted(name)).willReturn(false);
        PlayerAuth auth = PlayerAuth.builder().name(name).build();
        given(dataSource.getAuth(name)).willReturn(auth);
        FailedVerificationException exception = new FailedVerificationException(MessageKey.NOT_OWNER_ERROR);
        doThrow(exception).when(onJoinVerifier).checkNameRestrictions(eq(name), any(InetAddress.class));
        String message = "Not your account!";
        given(messages.retrieveSingle(name, exception.getReason(), exception.getArgs())).willReturn(message);

        listener.onAsyncPlayerPreLoginEventHighest(event);

        verify(onJoinVerifier).checkNameRestrictions(eq(name), any(InetAddress.class));
        verify(event).setKickMessage(message);
        verify(event).setLoginResult(AsyncPlayerPreLoginEvent.Result.KICK_OTHER);
    }

    @Test
    void shouldAbortPlayerJoinForInvalidName() throws FailedVerificationException {
        String name = "inval!dName";
        UUID uniqueId = TEST_PLAYER_UUID;
        InetAddress ip = createInetAddress("33.32.33.33");
        AsyncPlayerPreLoginEvent event = inlineSpy(new AsyncPlayerPreLoginEvent(name, ip, uniqueId));
        given(validationService.isUnrestricted(name)).willReturn(false);
        FailedVerificationException exception = new FailedVerificationException(
            MessageKey.INVALID_NAME_CHARACTERS, "[a-z]");
        doThrow(exception).when(onJoinVerifier).checkIsValidName(name, uniqueId);
        String message = "Invalid characters!";
        given(messages.retrieveSingle(name, exception.getReason(), exception.getArgs())).willReturn(message);

        listener.onAsyncPlayerPreLoginEventLowest(event);

        verify(validationService).isUnrestricted(name);
        verify(onJoinVerifier).checkIsValidName(name, uniqueId);
        // Check that we don't talk with the data source before performing checks that don't require it
        verifyNoInteractions(dataSource);
        verify(event).setKickMessage(message);
        verify(event).setLoginResult(AsyncPlayerPreLoginEvent.Result.KICK_OTHER);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("quitMessageCases")
    void shouldHandleQuitMessage(String description, boolean removeLeaveMessage,
                                 boolean removeUnloggedLeaveMessage, String expectedQuitMessage) {
        given(settings.getProperty(RegistrationSettings.REMOVE_LEAVE_MESSAGE)).willReturn(removeLeaveMessage);
        if (!removeLeaveMessage) {
            given(settings.getProperty(RegistrationSettings.REMOVE_UNLOGGED_LEAVE_MESSAGE))
                .willReturn(removeUnloggedLeaveMessage);
        }
        Player player = mock(Player.class, withSettings().mockMaker(MockMakers.INLINE));
        PlayerQuitEvent event = new PlayerQuitEvent(player, "quit message");
        given(listenerService.shouldCancelEvent(player)).willReturn(true); // player not authenticated
        if (!removeLeaveMessage && removeUnloggedLeaveMessage) {
            given(listenerService.shouldCancelEvent(event)).willReturn(true);
        }

        listener.onPlayerQuit(event);

        if (expectedQuitMessage == null) {
            assertThat(event.getQuitMessage(), nullValue());
        } else {
            assertThat(event.getQuitMessage(), equalTo(expectedQuitMessage));
        }
        verify(management).performQuit(player);
    }

    private static Stream<Arguments> quitMessageCases() {
        return Stream.of(
            Arguments.of("remove leave message for everyone", true, false, null),
            Arguments.of("remove message for unlogged player", false, true, null),
            Arguments.of("keep quit message", false, false, "quit message"));
    }

    @Test
    void shouldRestoreLimboDataSynchronouslyOnQuitOfPlayerInLimbo() {
        Player player = mock(Player.class, withSettings().mockMaker(MockMakers.INLINE));
        mockLeaveMessagesDisabled();
        given(listenerService.shouldCancelEvent(player)).willReturn(true);
        given(limboService.hasLimboPlayer(player)).willReturn(true);
        PlayerQuitEvent event = new PlayerQuitEvent(player, "quit");

        listener.onPlayerQuit(event);

        verify(limboService).restoreData(player);
        verify(limboService, never()).restoreSpeedSync(player);
        verify(management).performQuit(player);
    }

    @Test
    void shouldRestoreRevokedOperatorOnQuitOfPlayerWithoutLimbo() {
        Player player = mock(Player.class, withSettings().mockMaker(MockMakers.INLINE));
        mockLeaveMessagesDisabled();
        given(listenerService.shouldCancelEvent(player)).willReturn(true);
        given(limboService.hasLimboPlayer(player)).willReturn(false);
        PlayerQuitEvent event = new PlayerQuitEvent(player, "quit");

        listener.onPlayerQuit(event);

        verify(limboService).restorePendingOperator(player);
        verify(limboService).restoreSpeedSync(player);
        verify(limboService, never()).restoreData(player);
        verify(management).performQuit(player);
    }

    @Test
    void shouldRunQuitProcessEvenIfSavingQuitStateFails() {
        Player player = mock(Player.class, withSettings().mockMaker(MockMakers.INLINE));
        mockLeaveMessagesDisabled();
        given(listenerService.shouldCancelEvent(player)).willReturn(false);
        given(player.getServer()).willThrow(new UnsupportedOperationException("cross-region access"));
        PlayerQuitEvent event = new PlayerQuitEvent(player, "quit");

        assertThrows(UnsupportedOperationException.class, () -> listener.onPlayerQuit(event));

        verify(management).performQuit(player);
    }

    @ParameterizedTest(name = "owned by current region: {0}")
    @ValueSource(booleans = {true, false})
    @SuppressWarnings("unchecked")
    void shouldSaveOnlyEnderPearlsOwnedByCurrentRegion(boolean ownedByCurrentThread) {
        Player player = mock(Player.class, withSettings().mockMaker(MockMakers.INLINE));
        Server server = mock(Server.class, withSettings().mockMaker(MockMakers.INLINE));
        World world = mock(World.class, withSettings().mockMaker(MockMakers.INLINE));
        EnderPearl pearl = mock(EnderPearl.class, withSettings().mockMaker(MockMakers.INLINE));

        mockLeaveMessagesDisabled();
        given(listenerService.shouldCancelEvent(player)).willReturn(false);
        given(player.getServer()).willReturn(server);
        given(server.getWorlds()).willReturn(Collections.singletonList(world));
        given(world.getEntitiesByClass(EnderPearl.class)).willReturn(Collections.singletonList(pearl));
        given(bukkitService.isOwnedByCurrentThread(pearl)).willReturn(ownedByCurrentThread);

        PlayerQuitEvent event = new PlayerQuitEvent(player, "quit");

        if (ownedByCurrentThread) {
            World pearlWorld = mock(World.class, withSettings().mockMaker(MockMakers.INLINE));
            Location pearlLocation = new Location(pearlWorld, 10.0, 65.0, -1.0);
            org.bukkit.util.Vector pearlVelocity = new org.bukkit.util.Vector(0.1, 0.2, 0.3);
            given(pearl.getShooter()).willReturn(player);
            given(pearl.getUniqueId()).willReturn(UUID.nameUUIDFromBytes("quit-pearl".getBytes()));
            given(pearl.getLocation()).willReturn(pearlLocation);
            given(pearl.getVelocity()).willReturn(pearlVelocity);

            org.mockito.ArgumentCaptor<Collection<fr.xephi.authme.data.limbo.EnderPearlRestoreData>> captor =
                org.mockito.ArgumentCaptor.forClass(Collection.class);
            listener.onPlayerQuit(event);

            verify(limboService).saveEnderPearlsForPlayer(eq(player), captor.capture());
            Collection<fr.xephi.authme.data.limbo.EnderPearlRestoreData> pearls = captor.getValue();
            assertThat(pearls, hasSize(1));
            fr.xephi.authme.data.limbo.EnderPearlRestoreData savedPearl = pearls.iterator().next();
            assertThat(savedPearl.getLocation(), equalTo(pearlLocation));
            assertThat(savedPearl.getVelocity(), equalTo(pearlVelocity));
        } else {
            listener.onPlayerQuit(event);

            verify(pearl, never()).getShooter();
            verify(limboService, never()).saveEnderPearlsForPlayer(eq(player), any(Collection.class));
        }
        verify(management).performQuit(player);
    }

    @Test
    void shouldHandleInventoryClick() {
        verifyCancellation(InventoryClickEvent.class, InventoryClickEvent::getWhoClicked,
            listener::onPlayerInventoryClick, event -> verify(event, only()).getWhoClicked());
    }

    @Test
    void shouldHandleInventoryDrag() {
        verifyCancellation(InventoryDragEvent.class, InventoryDragEvent::getWhoClicked,
            listener::onPlayerInventoryDrag, event -> verify(event, only()).getWhoClicked());
    }

    @ParameterizedTest(name = "sign change, shouldCancel={0}")
    @ValueSource(booleans = {true, false})
    void shouldHandleSignChange(boolean shouldCancel) {
        SignChangeEvent event = mock(SignChangeEvent.class, withSettings().mockMaker(MockMakers.INLINE));
        Player player = mock(Player.class, withSettings().mockMaker(MockMakers.INLINE));
        given(event.getPlayer()).willReturn(player);
        given(listenerService.shouldCancelEvent(player)).willReturn(shouldCancel);

        listener.onSignChange(event);

        if (shouldCancel) {
            verify(event).setCancelled(true);
        } else {
            verify(event, only()).getPlayer();
        }
    }

    /**
     * Checks that a handler cancels the event for an unauthenticated player and leaves it untouched
     * for an authenticated one. {@code verifyAllowedEvent} asserts which getter the handler used
     * on the allowed path so that no other event call goes unnoticed.
     */
    private <T extends Event & Cancellable> void verifyCancellation(Class<T> eventClass,
                                                                     Function<T, HumanEntity> playerGetter,
                                                                     Consumer<T> handler,
                                                                     Consumer<T> verifyAllowedEvent) {
        T event = mock(eventClass);
        HumanEntity unauthenticated = mock(Player.class, withSettings().mockMaker(MockMakers.INLINE));
        given(playerGetter.apply(event)).willReturn(unauthenticated);
        given(listenerService.shouldCancelEvent(unauthenticated)).willReturn(true);
        handler.accept(event);
        verify(event).setCancelled(true);

        T allowedEvent = mock(eventClass);
        HumanEntity authenticated = mock(Player.class, withSettings().mockMaker(MockMakers.INLINE));
        given(playerGetter.apply(allowedEvent)).willReturn(authenticated);
        given(listenerService.shouldCancelEvent(authenticated)).willReturn(false);
        handler.accept(allowedEvent);
        verifyAllowedEvent.accept(allowedEvent);
    }

    @Test
    void shouldAllowInventoryOpen() {
        HumanEntity player = mock(Player.class, withSettings().mockMaker(MockMakers.INLINE));
        InventoryView transaction = mock(InventoryView.class, withSettings().mockMaker(MockMakers.INLINE));
        InventoryOpenEvent event = new InventoryOpenEvent(transaction);
        given(event.getPlayer()).willReturn(player);
        given(listenerService.shouldCancelEvent(player)).willReturn(false);

        listener.onPlayerInventoryOpen(event);

        assertThat(event.isCancelled(), equalTo(false));
        verifyNoInteractions(bukkitService);
    }

    @Test
    void shouldCancelInventoryOpen() {
        HumanEntity player = mock(Player.class, withSettings().mockMaker(MockMakers.INLINE));
        InventoryView transaction = mock(InventoryView.class, withSettings().mockMaker(MockMakers.INLINE));
        given(settings.getProperty(RestrictionSettings.UNRESTRICTED_INVENTORIES)).willReturn(Collections.emptySet());
        InventoryOpenEvent event = new InventoryOpenEvent(transaction);
        given(event.getPlayer()).willReturn(player);
        given(listenerService.shouldCancelEvent(player)).willReturn(true);
        setBukkitServiceToScheduleSyncDelayedTaskWithEntityAndDelay(bukkitService);

        listener.onPlayerInventoryOpen(event);

        assertThat(event.isCancelled(), equalTo(true));
        verify(player).closeInventory();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("joinMessageCases")
    void shouldHandleJoinMessage(String description, boolean removeMessage, String customMessage,
                                 boolean delayMessage, String playerName, String displayName,
                                 String expectedJoinMessage, String expectedPutMessage) {
        Player player = mock(Player.class, withSettings().mockMaker(MockMakers.INLINE));
        if (playerName != null) {
            given(player.getName()).willReturn(playerName);
            given(player.getDisplayName()).willReturn(displayName);
        }
        PlayerJoinEvent event = new PlayerJoinEvent(player, "The player joined");
        given(settings.getProperty(RegistrationSettings.REMOVE_JOIN_MESSAGE)).willReturn(removeMessage);
        if (!removeMessage) {
            given(settings.getProperty(RegistrationSettings.CUSTOM_JOIN_MESSAGE)).willReturn(customMessage);
            given(settings.getProperty(RegistrationSettings.DELAY_JOIN_MESSAGE)).willReturn(delayMessage);
        }

        listener.onJoinMessage(event);

        if (expectedJoinMessage == null) {
            assertThat(event.getJoinMessage(), nullValue());
        } else {
            assertThat(event.getJoinMessage(), equalTo(expectedJoinMessage));
        }
        if (expectedPutMessage == null) {
            verifyNoInteractions(joinMessageService);
        } else {
            verify(joinMessageService).putMessage(playerName, expectedPutMessage);
        }
    }

    private static Stream<Arguments> joinMessageCases() {
        return Stream.of(
            Arguments.of("keep message", false, "", false, null, null, "The player joined", null),
            Arguments.of("remove message", true, null, false, null, null, null, null),
            Arguments.of("custom message", false, "Hello {PLAYERNAME} (aka {DISPLAYNAME})", false,
                "doooew", "Displ", "Hello doooew (aka Displ)", null),
            Arguments.of("delay message", false, "{PLAYERNAME} is joining us", true,
                "thename0", "(not used)", null, "thename0 is joining us"));
    }

    private void mockLeaveMessagesDisabled() {
        given(settings.getProperty(RegistrationSettings.REMOVE_LEAVE_MESSAGE)).willReturn(false);
        given(settings.getProperty(RegistrationSettings.REMOVE_UNLOGGED_LEAVE_MESSAGE)).willReturn(false);
    }

    /**
     * {@link PlayerCommandPreprocessEvent} gets the list of online players from the player's server.
     * This method creates a Player mock with all necessary mocked behavior.
     *
     * @return Player mock
     */
    private static Player playerWithMockedServer() {
        Server server = mock(Server.class, withSettings().mockMaker(MockMakers.INLINE));
        given(server.getOnlinePlayers()).willReturn(Collections.emptyList());
        Player player = mock(Player.class, withSettings().mockMaker(MockMakers.INLINE));
        given(player.getServer()).willReturn(server);
        return player;
    }

    private static AsyncPlayerPreLoginEvent newPreLoginEvent(String ip) {
        return inlineSpy(new AsyncPlayerPreLoginEvent(TEST_PLAYER_NAME, createInetAddress(ip), TEST_PLAYER_UUID));
    }

    private static PlayerCommandPreprocessEvent mockCommandEvent(String message) {
        PlayerCommandPreprocessEvent commandEvent = mock(PlayerCommandPreprocessEvent.class, withSettings().mockMaker(MockMakers.INLINE));
        given(commandEvent.getMessage()).willReturn(message);
        return commandEvent;
    }

    private static AsyncPlayerChatEvent newAsyncChatEvent() {
        Player player = mock(Player.class, withSettings().mockMaker(MockMakers.INLINE));
        List<Player> recipients = Arrays.asList(mock(Player.class, withSettings().mockMaker(MockMakers.INLINE)), mock(Player.class, withSettings().mockMaker(MockMakers.INLINE)), mock(Player.class, withSettings().mockMaker(MockMakers.INLINE)));
        return inlineSpy(new AsyncPlayerChatEvent(true, player, "Test message", new HashSet<>(recipients)));
    }

    private static void verifyNoModifyingCalls(PlayerMoveEvent event) {
        verify(event, atLeast(0)).getPlayer();
        verify(event, atLeast(0)).getFrom();
        verify(event, atLeast(0)).getTo();
        verifyNoMoreInteractions(event);
    }

    private static void verifyNoModifyingCalls(AsyncPlayerPreLoginEvent event) {
        verify(event, atLeast(0)).getLoginResult();
        verify(event, atLeast(0)).getAddress();
        verify(event, atLeast(0)).getName();
        verify(event, atLeast(0)).getUniqueId();
        verifyNoMoreInteractions(event);
    }

    @SuppressWarnings("unchecked")
    private static <T> T inlineSpy(T instance) {
        return mock((Class<T>) instance.getClass(), withSettings().spiedInstance(instance)
            .defaultAnswer(CALLS_REAL_METHODS).mockMaker(MockMakers.INLINE));
    }
}
