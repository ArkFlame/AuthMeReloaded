package fr.xephi.authme.settings;

import com.google.common.io.Files;
import fr.xephi.authme.TestHelper;
import fr.xephi.authme.platform.BukkitCompatibilityAdapter;
import fr.xephi.authme.platform.TeleportAdapter;
import fr.xephi.authme.service.PluginHookService;
import fr.xephi.authme.settings.properties.RestrictionSettings;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockMakers;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;
import static org.mockito.Mockito.mockStatic;

/**
 * Test for {@link SpawnLoader}.
 */
@ExtendWith(MockitoExtension.class)
class SpawnLoaderTest {

    private SpawnLoader spawnLoader;

    @Mock(mockMaker = MockMakers.INLINE)
    private Settings settings;

    @Mock(mockMaker = MockMakers.INLINE)
    private PluginHookService pluginHookService;

    @Mock(mockMaker = MockMakers.INLINE)
    private TeleportAdapter teleportAdapter;

    @Mock(mockMaker = MockMakers.INLINE)
    private BukkitCompatibilityAdapter compatibilityAdapter;

    @TempDir
    File testFolder;

    @BeforeAll
    static void setUpLogger() {
        TestHelper.setupLogger();
    }

    @BeforeEach
    void setup() throws IOException {
        // Copy test config into a new temporary folder
        File source = TestHelper.getJarFile(TestHelper.PROJECT_ROOT + "settings/spawn-firstspawn.yml");
        File destination = new File(testFolder, "spawn.yml");
        Files.copy(source, destination);

        // Create a settings mock with default values
        given(settings.getProperty(RestrictionSettings.SPAWN_PRIORITY))
            .willReturn("authme, essentials, multiverse, default");
        spawnLoader = new SpawnLoader(testFolder, settings, pluginHookService, teleportAdapter, compatibilityAdapter);
    }

    private void reloadWithPriority(String priority) {
        given(settings.getProperty(RestrictionSettings.SPAWN_PRIORITY)).willReturn(priority);
        spawnLoader.reload();
    }

    @Test
    void shouldSetSpawn() {
        // given
        World world = mock(World.class, withSettings().mockMaker(MockMakers.INLINE));
        given(world.getName()).willReturn("new_world");
        Location newSpawn = new Location(world, 123, 45.0, -67.89);

        // when
        boolean result = spawnLoader.setSpawn(newSpawn);

        // then
        assertThat(result, equalTo(true));
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(new File(testFolder, "spawn.yml"));
        assertThat(configuration.getDouble("spawn.x"), equalTo(123.0));
        assertThat(configuration.getDouble("spawn.y"), equalTo(45.0));
        assertThat(configuration.getDouble("spawn.z"), equalTo(-67.89));
        assertThat(configuration.getString("spawn.world"), equalTo("new_world"));
    }

    @Test
    void shouldReturnBedSpawnLocationForDeadPlayer() {
        // given
        Player player = mock(Player.class, withSettings().mockMaker(MockMakers.INLINE));
        given(player.getHealth()).willReturn(0.0);
        World world = mock(World.class, withSettings().mockMaker(MockMakers.INLINE));
        Location bedSpawnLocation = new Location(world, 10.0, 70.0, -3.0);
        given(teleportAdapter.getPlayerRespawnLocation(player)).willReturn(bedSpawnLocation);

        // when
        Location result = spawnLoader.getPlayerLocationOrSpawn(player);

        // then
        assertThat(result, equalTo(bedSpawnLocation));
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "throws"})
    void shouldFallbackToConfiguredSpawnIfRespawnLocationUnavailable(String mode) {
        // given
        reloadWithPriority("default");

        Player player = mock(Player.class, withSettings().mockMaker(MockMakers.INLINE));
        given(player.getHealth()).willReturn(0.0);
        if ("throws".equals(mode)) {
            given(teleportAdapter.getPlayerRespawnLocation(player)).willThrow(IllegalStateException.class);
        } else {
            given(teleportAdapter.getPlayerRespawnLocation(player)).willReturn(null);
        }
        World world = mock(World.class, withSettings().mockMaker(MockMakers.INLINE));
        Location worldSpawn = new Location(world, 5.0, 65.0, 5.0);
        given(player.getWorld()).willReturn(world);
        given(world.getSpawnLocation()).willReturn(worldSpawn);

        // when
        Location result = spawnLoader.getPlayerLocationOrSpawn(player);

        // then
        assertThat(result, equalTo(worldSpawn));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 5})
    void shouldReturnExactWorldSpawnWhenNoSafeSpotIsAvailable(int radius) {
        // given
        reloadWithPriority("server");

        World world = mock(World.class, withSettings().mockMaker(MockMakers.INLINE));
        Location worldSpawn = new Location(world, 10.0, 64.0, -5.0);
        given(world.getSpawnLocation()).willReturn(worldSpawn);
        given(compatibilityAdapter.getSpawnRadius(world)).willReturn(radius);
        if (radius > 0) {
            Block solid = mock(Block.class, withSettings().mockMaker(MockMakers.INLINE));
            given(compatibilityAdapter.isBlockPassable(solid)).willReturn(false);
            given(world.getBlockAt(anyInt(), anyInt(), anyInt())).willReturn(solid);
        }

        // when
        Location result = spawnLoader.getSpawnLocation(world);

        // then
        assertThat(result, equalTo(worldSpawn));
    }

    @Test
    void shouldReturnExactYAndFaceTowardCenterWhenBaseYIsAlreadySafe() {
        // given
        reloadWithPriority("server");

        World world = mock(World.class, withSettings().mockMaker(MockMakers.INLINE));
        // worldSpawn at (100, 64, 200); place the result due east (+X) so the expected yaw is -90°
        Location worldSpawn = new Location(world, 100.0, 64.0, 200.0);
        given(world.getSpawnLocation()).willReturn(worldSpawn);
        given(compatibilityAdapter.getSpawnRadius(world)).willReturn(10);

        Block passable = mock(Block.class, withSettings().mockMaker(MockMakers.INLINE));
        given(compatibilityAdapter.isBlockPassable(passable)).willReturn(true);
        given(world.getBlockAt(anyInt(), eq(64), anyInt()))
            .willReturn(passable);
        given(world.getBlockAt(anyInt(), eq(65), anyInt()))
            .willReturn(passable);

        // when
        Location result = spawnLoader.getSpawnLocation(world);

        // then – position within radius, Y unchanged, pitch = 0
        assertThat(result.getX(), both(greaterThanOrEqualTo(90.5)).and(lessThanOrEqualTo(110.5)));
        assertThat(result.getZ(), both(greaterThanOrEqualTo(190.5)).and(lessThanOrEqualTo(210.5)));
        assertThat(result.getY(), equalTo(64.0));
        assertThat((double) result.getPitch(), closeTo(0.0, 0.001));
        // yaw must point from the result position toward worldSpawn (100, 200)
        double expectedYaw = Math.toDegrees(Math.atan2(-(100.0 - result.getX()), 200.0 - result.getZ()));
        assertThat((double) result.getYaw(), closeTo(expectedYaw, 0.001));
    }

    @ParameterizedTest
    @MethodSource("safeSpotCases")
    void shouldFindNearestSafeSpotY(int baseY, int expectedY, int[] solidYs, int[] passableYs) {
        // given
        reloadWithPriority("server");

        World world = mock(World.class, withSettings().mockMaker(MockMakers.INLINE));
        Location worldSpawn = new Location(world, 0.0, baseY, 0.0);
        given(world.getSpawnLocation()).willReturn(worldSpawn);
        given(compatibilityAdapter.getSpawnRadius(world)).willReturn(5);

        Block passable = mock(Block.class, withSettings().mockMaker(MockMakers.INLINE));
        given(compatibilityAdapter.isBlockPassable(passable)).willReturn(true);
        Block solid = mock(Block.class, withSettings().mockMaker(MockMakers.INLINE));
        given(compatibilityAdapter.isBlockPassable(solid)).willReturn(false);
        for (int y : solidYs) {
            given(world.getBlockAt(anyInt(), eq(y), anyInt())).willReturn(solid);
        }
        for (int y : passableYs) {
            given(world.getBlockAt(anyInt(), eq(y), anyInt())).willReturn(passable);
        }

        // when
        Location result = spawnLoader.getSpawnLocation(world);

        // then
        assertThat(result.getY(), equalTo((double) expectedY));
    }

    private static Stream<Arguments> safeSpotCases() {
        return Stream.of(
            // foot passable but head solid at baseY: search downward to the first 2-block gap
            Arguments.of(70, 69, new int[]{71}, new int[]{70, 69}),
            // baseY inside a solid block: search upward to the first 2-block gap
            Arguments.of(60, 63, new int[]{60, 61, 62}, new int[]{63, 64}));
    }

    @Test
    void shouldIgnoreNullCandidateWorldSpawnLocations() {
        // given
        reloadWithPriority("default");

        World world = mock(World.class, withSettings().mockMaker(MockMakers.INLINE));
        Location worldSpawn = new Location(world, 0.0, 0.0, 0.0);
        given(world.getSpawnLocation()).willReturn(worldSpawn);

        World candidateWorld = mock(World.class, withSettings().mockMaker(MockMakers.INLINE));
        given(candidateWorld.getSpawnLocation()).willReturn(null);

        // when
        Location result;
        try (MockedStatic<Bukkit> bukkitMock = mockStatic(Bukkit.class, withSettings().mockMaker(MockMakers.INLINE))) {
            bukkitMock.when(Bukkit::getWorlds).thenReturn(List.of(candidateWorld));
            result = spawnLoader.getSpawnLocation(world);
        }

        // then
        assertThat(result, equalTo(worldSpawn));
    }

}
