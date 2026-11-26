package fr.xephi.authme.service;

import com.earth2me.essentials.Essentials;
import org.mockito.MockMakers;
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

import java.io.File;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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

    @BeforeAll
    static void setLogger() {
        TestHelper.setupLogger();
    }

    @Test
    void shouldHookIntoEssentials() {
        // given
        PluginManager pluginManager = mock(PluginManager.class, withSettings().mockMaker(MockMakers.INLINE));
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
        PluginManager pluginManager = mock(PluginManager.class, withSettings().mockMaker(MockMakers.INLINE));
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

        PluginManager pluginManager = mock(PluginManager.class, withSettings().mockMaker(MockMakers.INLINE));
        setPluginAvailable(pluginManager, ESSENTIALS, ess);
        PluginHookService pluginHookService = new PluginHookService(pluginManager);

        // when
        File dataFolder = pluginHookService.getEssentialsDataFolder();

        // then
        assertThat(dataFolder, equalTo(essDataFolder));
    }

    @Test
    void shouldReturnNullForUnhookedEssentials() {
        // given
        PluginManager pluginManager = mock(PluginManager.class, withSettings().mockMaker(MockMakers.INLINE));
        PluginHookService pluginHookService = new PluginHookService(pluginManager);

        // when
        File result = pluginHookService.getEssentialsDataFolder();

        // then
        assertThat(result, nullValue());
    }

    @Test
    void shouldSetSocialSpyStatus() {
        // given
        Player player = mock(Player.class, withSettings().mockMaker(MockMakers.INLINE));

        Essentials ess = mock(Essentials.class, withSettings().mockMaker(MockMakers.INLINE));
        User user = mock(User.class, withSettings().mockMaker(MockMakers.INLINE));
        given(ess.getUser(player)).willReturn(user);

        PluginManager pluginManager = mock(PluginManager.class, withSettings().mockMaker(MockMakers.INLINE));
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
        PluginHookService pluginHookService = new PluginHookService(mock(PluginManager.class, withSettings().mockMaker(MockMakers.INLINE)));

        // when/then
        pluginHookService.setEssentialsSocialSpyStatus(mock(Player.class, withSettings().mockMaker(MockMakers.INLINE)), false);
    }

    @Test
    void shouldUnhookEssentialsAndMultiverse() {
        // given
        PluginManager pluginManager = mock(PluginManager.class, withSettings().mockMaker(MockMakers.INLINE));
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
        PluginManager pluginManager = mock(PluginManager.class, withSettings().mockMaker(MockMakers.INLINE));
        given(pluginManager.isPluginEnabled(anyString())).willReturn(true);
        doThrow(IllegalStateException.class).when(pluginManager).getPlugin(anyString());

        // when
        PluginHookService pluginHookService = new PluginHookService(pluginManager);

        // then
        assertThat(pluginHookService.isEssentialsAvailable(), equalTo(false));
        assertThat(pluginHookService.isCmiAvailable(), equalTo(false));
        assertThat(pluginHookService.isMultiverseAvailable(), equalTo(false));
    }

    @Test
    void shouldReturnNullForUnavailableMultiverse() {
        // given
        PluginManager pluginManager = mock(PluginManager.class, withSettings().mockMaker(MockMakers.INLINE));
        PluginHookService pluginHookService = new PluginHookService(pluginManager);
        World world = mock(World.class, withSettings().mockMaker(MockMakers.INLINE));

        // when
        Location result = pluginHookService.getMultiverseSpawn(world);

        // then
        assertThat(result, nullValue());
    }

    @Test
    void shouldGetMultiverseSpawn() {
        // given
        Location location = mock(Location.class, withSettings().mockMaker(MockMakers.INLINE));
        MultiverseWorld multiverseWorld = mock(MultiverseWorld.class, withSettings().mockMaker(MockMakers.INLINE));
        given(multiverseWorld.getSpawnLocation()).willReturn(location);

        World world = mock(World.class, withSettings().mockMaker(MockMakers.INLINE));
        MVWorldManager mvWorldManager = mock(MVWorldManager.class, withSettings().mockMaker(MockMakers.INLINE));
        given(mvWorldManager.isMVWorld(world)).willReturn(true);
        given(mvWorldManager.getMVWorld(world)).willReturn(multiverseWorld);
        MultiverseCore multiverse = mock(MultiverseCore.class, withSettings().mockMaker(MockMakers.INLINE));
        given(multiverse.getMVWorldManager()).willReturn(mvWorldManager);

        PluginManager pluginManager = mock(PluginManager.class, withSettings().mockMaker(MockMakers.INLINE));
        setPluginAvailable(pluginManager, MULTIVERSE, multiverse);
        PluginHookService pluginHookService = new PluginHookService(pluginManager);

        // when
        Location spawn = pluginHookService.getMultiverseSpawn(world);

        // then
        assertThat(spawn, equalTo(location));
        verify(mvWorldManager).isMVWorld(world);
        verify(mvWorldManager).getMVWorld(world);
        verify(multiverseWorld).getSpawnLocation();
    }

    @Test
    void shouldReturnNullForNonMvWorld() {
        // given
        World world = mock(World.class, withSettings().mockMaker(MockMakers.INLINE));
        MVWorldManager mvWorldManager = mock(MVWorldManager.class, withSettings().mockMaker(MockMakers.INLINE));
        given(mvWorldManager.isMVWorld(world)).willReturn(false);

        PluginManager pluginManager = mock(PluginManager.class, withSettings().mockMaker(MockMakers.INLINE));
        MultiverseCore multiverse = mock(MultiverseCore.class, withSettings().mockMaker(MockMakers.INLINE));
        setPluginAvailable(pluginManager, MULTIVERSE, multiverse);
        given(multiverse.getMVWorldManager()).willReturn(mvWorldManager);
        PluginHookService pluginHookService = new PluginHookService(pluginManager);

        // when
        Location spawn = pluginHookService.getMultiverseSpawn(world);

        // then
        assertThat(spawn, nullValue());
        verify(mvWorldManager).isMVWorld(world);
        verify(mvWorldManager, never()).getMVWorld(world);
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
