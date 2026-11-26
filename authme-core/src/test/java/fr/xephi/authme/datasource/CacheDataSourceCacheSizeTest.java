package fr.xephi.authme.datasource;

import fr.xephi.authme.data.auth.PlayerCache;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.mockito.Mockito.mock;

/**
 * Test for the bounded size of the auth cache in {@link CacheDataSource}.
 */
class CacheDataSourceCacheSizeTest {

    @Test
    void shouldBoundCacheSize() {
        CacheDataSource dataSource = new CacheDataSource(mock(DataSource.class), mock(PlayerCache.class));
        try {
            int inserts = CacheDataSource.MAX_CACHED_AUTHS + 100;
            for (int i = 0; i < inserts; i++) {
                dataSource.getAuth("player" + i);
            }
            assertThat(dataSource.getCachedAuths().size(),
                lessThanOrEqualTo((long) CacheDataSource.MAX_CACHED_AUTHS));
        } finally {
            dataSource.closeConnection();
        }
    }
}
