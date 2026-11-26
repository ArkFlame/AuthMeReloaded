package fr.xephi.authme.command.executable.authme.debug;

import fr.xephi.authme.ReflectionTestUtils;
import fr.xephi.authme.TestHelper;
import fr.xephi.authme.data.auth.PlayerCache;
import fr.xephi.authme.data.limbo.LimboPlayer;
import fr.xephi.authme.data.limbo.LimboService;
import fr.xephi.authme.datasource.CacheDataSource;
import fr.xephi.authme.datasource.DataSource;
import org.bukkit.Location;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.Mockito.mock;

/**
 * Test for {@link DebugSectionUtils}.
 */
class DebugSectionUtilsTest {

    @BeforeEach
    void initMockLogger() {
        TestHelper.setupLogger();
    }

    @Test
    void shouldFormatLocation() {
        assertThat(DebugSectionUtils.formatLocation(0.0, 10.248592, -18934.2349023, "Main"),
            equalTo("(0, 10.25, -18934.23) in 'Main'"));
        Location location = new Location(null, 3.7777, 2.14156, 1);
        assertThat(DebugSectionUtils.formatLocation(location), equalTo("(3.78, 2.14, 1) in 'null'"));
        assertThat(DebugSectionUtils.formatLocation(null), equalTo("null"));
    }

    @Test
    void shouldFetchMapInLimboService() {
        LimboService limboService = mock(LimboService.class);
        Map<String, LimboPlayer> limboMap = createLimboServiceWithEntries(limboService);

        Map map = DebugSectionUtils.applyToLimboPlayersMap(limboService, Function.identity());
        assertThat(map, sameInstance(limboMap));

        Object result = DebugSectionUtils.applyToLimboPlayersMap(limboService, m -> {
            throw new IllegalStateException();
        });
        assertThat(result, nullValue());
    }

    @Test
    void shouldUnwrapSourceFromCacheDataSource() {
        DataSource dataSource = mock(DataSource.class);
        assertThat(DebugSectionUtils.unwrapSourceFromCacheDataSource(dataSource), equalTo(dataSource));

        DataSource source = mock(DataSource.class);
        CacheDataSource cacheDataSource = new CacheDataSource(source, mock(PlayerCache.class));
        assertThat(DebugSectionUtils.unwrapSourceFromCacheDataSource(cacheDataSource), equalTo(source));
    }

    @Test
    void shouldCastOrReturnNull() {
        assertThat(DebugSectionUtils.castToTypeOrNull("test", String.class), equalTo("test"));
        assertThat(DebugSectionUtils.castToTypeOrNull("test", Integer.class), nullValue());
        assertThat(DebugSectionUtils.castToTypeOrNull(5, String.class), nullValue());
        assertThat(DebugSectionUtils.castToTypeOrNull(5, Integer.class), equalTo(5));
    }

    private static Map<String, LimboPlayer> createLimboServiceWithEntries(LimboService limboService) {
        Map<String, LimboPlayer> limboMap = new HashMap<>();
        ReflectionTestUtils.setField(LimboService.class, limboService, "entries", limboMap);
        return limboMap;
    }
}
