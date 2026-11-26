package fr.xephi.authme.service;

import fr.xephi.authme.data.auth.PlayerCache;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.platform.TeleportAdapter;
import fr.xephi.authme.settings.Settings;
import fr.xephi.authme.settings.SpawnLoader;
import fr.xephi.authme.settings.properties.RegistrationSettings;
import fr.xephi.authme.settings.properties.RestrictionSettings;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.stream.Stream;

import static fr.xephi.authme.service.BukkitServiceTestHelper.setBukkitServiceToScheduleSyncEntityTaskFromOptionallyAsyncTask;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Tests that first-time players are spawned at the configured first spawn (issue #3094).
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class TeleportationServiceFirstSpawnTest {

    @InjectMocks
    private TeleportationService teleportationService;

    @Mock
    private Settings settings;

    @Mock
    private BukkitService bukkitService;

    @Mock
    private SpawnLoader spawnLoader;

    @Mock
    private PlayerCache playerCache;

    @Mock
    private DataSource dataSource;

    @Mock
    private TeleportAdapter teleportAdapter;

    @Test
    void shouldSendNewPlayerToFirstSpawnOnJoin() {
        // given
        givenSpawnSettings("authme,essentials,default");
        Player player = mock(Player.class);
        given(player.isOnline()).willReturn(true);
        Location firstSpawn = new Location(mock(World.class), 10.0, 64.0, -20.0);
        given(spawnLoader.getFirstSpawn()).willReturn(firstSpawn);
        setBukkitServiceToScheduleSyncEntityTaskFromOptionallyAsyncTask(bukkitService);

        // when
        teleportationService.teleportOnJoin(player);

        // then
        verify(teleportAdapter).teleportPlayer(player, firstSpawn);
    }

    @Test
    void shouldUseFirstSpawnForNewPlayerInAsyncJoinHook() {
        // given
        givenSpawnSettings("authme,essentials,default");
        World world = mock(World.class);
        Location firstSpawn = new Location(world, 10.0, 64.0, -20.0);
        given(spawnLoader.getFirstSpawn()).willReturn(firstSpawn);

        // when
        Location result = teleportationService.prepareOnJoinSpawnLocation("Newbie", world);

        // then
        assertThat(result, equalTo(firstSpawn));
        verify(spawnLoader, never()).getSpawnLocation(any(World.class));
    }

    @ParameterizedTest(name = "priority \"{0}\" for {1}")
    @MethodSource("regularSpawnPriorities")
    void shouldKeepRegularSpawn(String spawnPriority, String playerName, boolean registered) {
        // given
        givenSpawnSettings(spawnPriority);
        World world = mock(World.class);
        Location regularSpawn = new Location(world, 1.0, 2.0, 3.0);
        given(dataSource.isAuthAvailable(playerName)).willReturn(registered);
        given(spawnLoader.getSpawnLocation(world)).willReturn(regularSpawn);

        // when
        Location result = teleportationService.prepareOnJoinSpawnLocation(playerName, world);

        // then
        assertThat(result, equalTo(regularSpawn));
        verify(spawnLoader, never()).getFirstSpawn();
    }

    private static Stream<Arguments> regularSpawnPriorities() {
        return Stream.of(
            Arguments.of("authme,essentials,default", "Veteran", true),
            Arguments.of("essentials,authme,default", "Newbie", false));
    }

    private void givenSpawnSettings(String spawnPriority) {
        given(settings.getProperty(RestrictionSettings.NO_TELEPORT)).willReturn(false);
        given(settings.getProperty(RestrictionSettings.SPAWN_PRIORITY)).willReturn(spawnPriority);
        given(settings.getProperty(RestrictionSettings.TELEPORT_UNAUTHED_TO_SPAWN)).willReturn(true);
        given(settings.getProperty(RegistrationSettings.FORCE)).willReturn(true);
    }
}
