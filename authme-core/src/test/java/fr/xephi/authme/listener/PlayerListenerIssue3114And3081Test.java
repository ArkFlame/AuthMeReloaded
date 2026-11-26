package fr.xephi.authme.listener;

import fr.xephi.authme.data.QuickCommandsProtectionManager;
import fr.xephi.authme.data.auth.PlayerCache;
import fr.xephi.authme.data.limbo.LimboService;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.message.Messages;
import fr.xephi.authme.permission.PermissionsManager;
import fr.xephi.authme.platform.ChatAdapter;
import fr.xephi.authme.platform.TeleportAdapter;
import fr.xephi.authme.process.Management;
import fr.xephi.authme.service.BukkitService;
import fr.xephi.authme.service.JoinMessageService;
import fr.xephi.authme.service.TeleportationService;
import fr.xephi.authme.service.ValidationService;
import fr.xephi.authme.settings.Settings;
import fr.xephi.authme.settings.SpawnLoader;
import fr.xephi.authme.settings.properties.RestrictionSettings;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * Tests for the duplicate-login kick handling (#3114) and the movement radius clamp (#3081).
 */
@ExtendWith(MockitoExtension.class)
class PlayerListenerIssue3114And3081Test {

    @InjectMocks
    private PlayerListener listener;

    @Mock
    private Settings settings;
    @Mock
    private Messages messages;
    @Mock
    private DataSource dataSource;
    @Mock
    private Management management;
    @Mock
    private BukkitService bukkitService;
    @Mock
    private SpawnLoader spawnLoader;
    @Mock
    private OnJoinVerifier onJoinVerifier;
    @Mock
    private ListenerService listenerService;
    @Mock
    private TeleportationService teleportationService;
    @Mock
    private ValidationService validationService;
    @Mock
    private JoinMessageService joinMessageService;
    @Mock
    private QuickCommandsProtectionManager quickCommandsProtectionManager;
    @Mock
    private PermissionsManager permissionsManager;
    @Mock
    private LimboService limboService;
    @Mock
    private ChatAdapter chatAdapter;
    @Mock
    private TeleportAdapter teleportAdapter;
    @Mock
    private PlayerCache playerCache;

    /**
     * #3114: a localized (or Folia) duplicate-login reason must still cancel the kick
     * when another online session of the same player exists.
     */
    @Test
    void shouldCancelLocalizedDuplicateLoginKick() {
        String reason = "Du bist von einem anderen Ort angemeldet.";
        given(settings.getProperty(RestrictionSettings.FORCE_SINGLE_SESSION)).willReturn(true);

        Player kicked = mock(Player.class);
        Player otherSession = mock(Player.class);
        given(kicked.getName()).willReturn("Tester");
        given(otherSession.getName()).willReturn("Tester");
        given(bukkitService.getOnlinePlayers()).willReturn(Arrays.asList(kicked, otherSession));
        given(playerCache.isAuthenticated("Tester")).willReturn(true);

        PlayerKickEvent event = new PlayerKickEvent(kicked, reason, "");

        listener.onPlayerKick(event);

        assertThat(event.isCancelled(), equalTo(true));
    }

    @Test
    void shouldNotCancelLocalizedKickWithoutDuplicateSession() {
        String reason = "Verboten";
        given(settings.getProperty(RestrictionSettings.FORCE_SINGLE_SESSION)).willReturn(true);
        given(chatAdapter.getKickReason(any())).willReturn(reason);

        Player kicked = mock(Player.class);
        PlayerKickEvent event = new PlayerKickEvent(kicked, reason, "");

        listener.onPlayerKick(event);

        assertThat(event.isCancelled(), equalTo(false));
    }

    /**
     * #3081: crossing the allowed movement radius should be prevented with setTo,
     * not by teleporting the player back to spawn.
     */
    @Test
    void shouldClampMovementAtRadiusInsteadOfTeleportingToSpawn() {
        given(settings.getProperty(RestrictionSettings.ALLOW_UNAUTHED_MOVEMENT)).willReturn(true);
        given(settings.getProperty(RestrictionSettings.ALLOWED_MOVEMENT_RADIUS)).willReturn(12);
        given(settings.getProperty(RestrictionSettings.NO_TELEPORT)).willReturn(false);

        World world = mock(World.class);
        Player player = mock(Player.class);
        given(player.getWorld()).willReturn(world);
        Location from = new Location(world, 8, 70, 8);
        Location to = new Location(world, 20, 70, 8);
        given(player.getLocation()).willReturn(from);
        PlayerMoveEvent event = spy(new PlayerMoveEvent(player, from, to));
        given(listenerService.shouldCancelEvent(player)).willReturn(true);
        Location spawn = new Location(world, 0, 70, 0);
        given(spawnLoader.getSpawnLocation(player)).willReturn(spawn);

        listener.onPlayerMove(event);

        verify(event).setTo(from);
        verify(teleportAdapter, never()).teleportPlayer(any(Player.class), any(Location.class));
    }
}
