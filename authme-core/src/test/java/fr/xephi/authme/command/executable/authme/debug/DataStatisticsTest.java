package fr.xephi.authme.command.executable.authme.debug;

import ch.jalu.injector.factory.SingletonStore;
import com.google.common.cache.LoadingCache;
import fr.xephi.authme.ReflectionTestUtils;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.data.auth.PlayerCache;
import fr.xephi.authme.data.limbo.LimboPlayer;
import fr.xephi.authme.data.limbo.LimboService;
import fr.xephi.authme.datasource.CacheDataSource;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.initialization.HasCleanup;
import fr.xephi.authme.initialization.Reloadable;
import fr.xephi.authme.initialization.SettingsDependent;
import fr.xephi.authme.output.ConsoleLoggerFactory;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Test for {@link DataStatistics}.
 */
@ExtendWith(MockitoExtension.class)
class DataStatisticsTest {

    @InjectMocks
    private DataStatistics dataStatistics;

    @Mock
    private DataSource dataSource;
    @Mock
    private PlayerCache playerCache;
    @Mock
    private LimboService limboService;
    @Mock
    private SingletonStore<Object> singletonStore;

    @BeforeEach
    void setUpLimboCacheMap() {
        Map<String, LimboPlayer> limboMap = new HashMap<>();
        limboMap.put("test", mock(LimboPlayer.class));
        ReflectionTestUtils.setField(LimboService.class, limboService, "entries", limboMap);
    }

    @Test
    void shouldOutputStatistics() {
        // given
        given(singletonStore.retrieveAllOfType()).willReturn(mockListOfSize(Object.class, 7));
        given(singletonStore.retrieveAllOfType(Reloadable.class)).willReturn(mockListOfSize(Reloadable.class, 4));
        given(singletonStore.retrieveAllOfType(SettingsDependent.class)).willReturn(mockListOfSize(SettingsDependent.class, 3));
        given(singletonStore.retrieveAllOfType(HasCleanup.class)).willReturn(mockListOfSize(HasCleanup.class, 2));
        given(dataSource.getAccountsRegistered()).willReturn(219);
        given(playerCache.getLogged()).willReturn(12);

        // The logger registry is JVM-wide and other tests may register loggers concurrently,
        // so create two known loggers and derive the expected count from the registry itself
        ConsoleLoggerFactory.get(String.class);
        ConsoleLoggerFactory.get(Integer.class);

        // when
        List<String> messages = new ArrayList<>(executeAndCaptureMessages());

        // then
        String loggerPrefix = "Total logger instances: ";
        String loggerLine = messages.stream().filter(m -> m.startsWith(loggerPrefix)).findFirst().get();
        messages.remove(loggerLine);
        assertThat(Integer.parseInt(loggerLine.substring(loggerPrefix.length())), greaterThanOrEqualTo(2));
        assertThat(messages, containsInAnyOrder(
            ChatColor.BLUE + "AuthMe statistics",
            "Singleton Java classes: 7",
            "(Reloadable: 4 / SettingsDependent: 3 / HasCleanup: 2)",
            "LimboPlayers in memory: 1",
            "Total players in DB: 219",
            "PlayerCache size: 12 (= logged in players)"));

        // Cached data source reports the number of cached PlayerAuth objects
        CacheDataSource cacheDataSource = mock(CacheDataSource.class);
        LoadingCache<String, Optional<PlayerAuth>> cache = mock(LoadingCache.class);
        given(cache.size()).willReturn(11L);
        given(cacheDataSource.getCachedAuths()).willReturn(cache);
        ReflectionTestUtils.setField(DataStatistics.class, dataStatistics, "dataSource", cacheDataSource);

        assertThat(executeAndCaptureMessages(), hasItem("Cached PlayerAuth objects: 11"));
    }

    private List<String> executeAndCaptureMessages() {
        CommandSender sender = mock(CommandSender.class);
        dataStatistics.execute(sender, Collections.emptyList());
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(sender, atLeastOnce()).sendMessage(captor.capture());
        return captor.getAllValues();
    }

    private static <T> List<T> mockListOfSize(Class<T> mockClass, int size) {
        T mock = mock(mockClass);
        return Collections.nCopies(size, mock);
    }
}
