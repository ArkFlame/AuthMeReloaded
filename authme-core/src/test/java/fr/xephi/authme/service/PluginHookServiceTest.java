package fr.xephi.authme.service;

import com.earth2me.essentials.Essentials;
import com.earth2me.essentials.User;
import com.onarandombox.MultiverseCore.MultiverseCore;
import com.onarandombox.MultiverseCore.api.MVWorldManager;
import com.onarandombox.MultiverseCore.api.MultiverseWorld;
import fr.xephi.authme.ReflectionTestUtils;
import fr.xephi.authme.TestHelper;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockMakers;

import java.io.File;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.withSettings;

/**
 * Test for {@link PluginHookService}.
 */
class PluginHookServiceTest {

    /** The plugin name of Essentials. */
    private static final String ESSENTIALS = "Essentials";
    /** The plugin name of CMI. */
    private static final String CMI = "CMI";
    /** The plugin name of Multiverse-Core. */
    private static final String MULTIVERSE = "Multiverse-Core";

    // PluginManager is an interface, so the default subclass mock maker suffices.
    // JUnit creates a new test instance per method, so this is a fresh mock for every test.
    private final PluginManager pluginManager = mock(PluginManager.class);

    @BeforeAll
    static void setUpOnce() {
        TestHelper.setupLogger();
    }

    @Test
    void shouldHookIntoEssentials() {
        // given
        PluginHookService pluginHookService = new PluginHookService(pluginManager);
        setPluginAvailable(pluginManager, ESSENTIALS, Essentials.class);
        assertThat(pluginHookService.isEssentialsAvailable(), equalTo(false));

        // when
        pluginHookService.tryHookToEssentials();

        // then
        assertThat(pluginHookService.isEssentialsAvailable(), equalTo(true));
    }

    @ParameterizedTest(name = "hook {0} at initialization")
    @MethodSource("pluginsAvailableAtInitialization")
    void shouldHookIntoPluginAtInitialization(String pluginName, Class<? extends Plugin> pluginClass,
                                              Predicate<PluginHookService> availability) {
        // given
        setPluginAvailable(pluginManager, pluginName, pluginClass);

        // when
        PluginHookService pluginHookService = new PluginHookService(pluginManager);

        // then
        assertThat(availability.test(pluginHookService), equalTo(true));
    }

    private static Stream<Arguments> pluginsAvailableAtInitialization() {
        return Stream.of(
            Arguments.of(ESSENTIALS, Essentials.class,
                (Predicate<PluginHookService>) PluginHookService::isEssentialsAvailable),
            Arguments.of(CMI, Plugin.class,
                (Predicate<PluginHookService>) PluginHookService::isCmiAvailable),
            Arguments.of(MULTIVERSE, MultiverseCore.class,
                (Predicate<PluginHookService>) PluginHookService::isMultiverseAvailable));
    }

    @Test
    void shouldReturnEssentialsDataFolder() {
        // given
        Essentials ess = mock(Essentials.class, withSettings().mockMaker(MockMakers.INLINE));
        File essDataFolder = new File("test/data-folder");
        // Need to set the data folder with reflections because getDataFolder() is declared final
        ReflectionTestUtils.setField(JavaPlugin.class, ess, "dataFolder", essDataFolder);
        given(ess.getDataFolder()).willCallRealMethod(); // Needed because static/final methods can be mocked

        setPluginAvailable(pluginManager, ESSENTIALS, ess);
        PluginHookService pluginHookService = new PluginHookService(pluginManager);

        // when
        File dataFolder = pluginHookService.getEssentialsDataFolder();

        // then
        assertThat(dataFolder, equalTo(essDataFolder));
    }

    @ParameterizedTest(name = "returns null when no plugin is hooked")
    @MethodSource("nullResultsWithoutHook")
    void shouldReturnNullWithoutHook(Function<PluginHookService, Object> methodCall) {
        // given
        PluginHookService pluginHookService = new PluginHookService(pluginManager);

        // when
        Object result = methodCall.apply(pluginHookService);

        // then
        assertThat(result, nullValue());
    }

    private static Stream<Arguments> nullResultsWithoutHook() {
        return Stream.of(
            Arguments.of((Function<PluginHookService, Object>) PluginHookService::getEssentialsDataFolder),
            Arguments.of((Function<PluginHookService, Object>) service -> service.getMultiverseSpawn(null)));
    }

    @Test
    void shouldSetSocialSpyStatus() {
        // given
        Player player = mock(Player.class);

        Essentials ess = mock(Essentials.class, withSettings().mockMaker(MockMakers.INLINE));
        User user = mock(User.class, withSettings().mockMaker(MockMakers.INLINE));
        given(ess.getUser(player)).willReturn(user);

        setPluginAvailable(pluginManager, ESSENTIALS, ess);
        PluginHookService pluginHookService = new PluginHookService(pluginManager);

        // when
        pluginHookService.setEssentialsSocialSpyStatus(player, true);

        // then
        verify(ess).getUser(player);
        verify(user).setSocialSpyEnabled(true);
    }

    @Test
    void shouldNotDoAnythingForUnhookedEssentials() {
        // given
        PluginHookService pluginHookService = new PluginHookService(pluginManager);

        // when/then
        pluginHookService.setEssentialsSocialSpyStatus(mock(Player.class), false);
    }

    @Test
    void shouldUnhookEssentialsAndMultiverse() {
        // given
        setPluginAvailable(pluginManager, ESSENTIALS, Essentials.class);
        setPluginAvailable(pluginManager, MULTIVERSE, MultiverseCore.class);
        PluginHookService pluginHookService = new PluginHookService(pluginManager);

        // when
        pluginHookService.unhookEssentials();
        pluginHookService.unhookMultiverse();

        // then
        assertThat(pluginHookService.isEssentialsAvailable(), equalTo(false));
        assertThat(pluginHookService.isMultiverseAvailable(), equalTo(false));
    }

    @Test
    void shouldHandlePluginRetrievalError() {
        // given
        given(pluginManager.isPluginEnabled(anyString())).willReturn(true);
        doThrow(IllegalStateException.class).when(pluginManager).getPlugin(anyString());

        // when
        PluginHookService pluginHookService = new PluginHookService(pluginManager);

        // then
        assertThat(pluginHookService.isEssentialsAvailable(), equalTo(false));
        assertThat(pluginHookService.isCmiAvailable(), equalTo(false));
        assertThat(pluginHookService.isMultiverseAvailable(), equalTo(false));
    }

    @ParameterizedTest(name = "multiverse spawn lookup for MV world: {0}")
    @ValueSource(booleans = {true, false})
    void shouldGetMultiverseSpawn(boolean isMvWorld) {
        // given
        // World only serves as an unstubbed lookup key.
        World world = mock(World.class);
        Location location = mock(Location.class);
        MultiverseWorld multiverseWorld = mock(MultiverseWorld.class);
        given(multiverseWorld.getSpawnLocation()).willReturn(location);

        MVWorldManager mvWorldManager = mock(MVWorldManager.class);
        given(mvWorldManager.isMVWorld(world)).willReturn(isMvWorld);
        given(mvWorldManager.getMVWorld(world)).willReturn(multiverseWorld);
        MultiverseCore multiverse = mock(MultiverseCore.class, withSettings().mockMaker(MockMakers.INLINE));
        given(multiverse.getMVWorldManager()).willReturn(mvWorldManager);

        setPluginAvailable(pluginManager, MULTIVERSE, multiverse);
        PluginHookService pluginHookService = new PluginHookService(pluginManager);

        // when
        Location spawn = pluginHookService.getMultiverseSpawn(world);

        // then
        assertThat(spawn, isMvWorld ? equalTo(location) : nullValue());
        verify(mvWorldManager).isMVWorld(world);
        verify(mvWorldManager, isMvWorld ? times(1) : never()).getMVWorld(world);
        if (isMvWorld) {
            verify(multiverseWorld).getSpawnLocation();
        }
    }

    private static void setPluginAvailable(PluginManager managerMock, String pluginName,
                                           Class<? extends Plugin> pluginClass) {
        setPluginAvailable(managerMock, pluginName, mock(pluginClass));
    }

    private static <T extends Plugin> void setPluginAvailable(PluginManager managerMock, String pluginName, T plugin) {
        given(managerMock.isPluginEnabled(pluginName)).willReturn(true);
        given(managerMock.getPlugin(pluginName)).willReturn(plugin);
    }

}
