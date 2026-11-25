package fr.xephi.authme.datasource;

import com.google.common.util.concurrent.ListeningExecutorService;
import fr.xephi.authme.data.auth.PlayerCache;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.mock;

/**
 * Test for the cache executor creation in {@link CacheDataSource}.
 */
class CacheDataSourceExecutorTest {

    @Test
    void shouldFallBackToCachedPoolWhenVirtualThreadMethodIsAbsent() throws Exception {
        ListeningExecutorService executor =
            CacheDataSource.createExecutorService(true, NoVirtualThreads.class);
        try {
            String threadName = executor.submit(
                (Callable<String>) () -> Thread.currentThread().getName()).get();
            assertThat(threadName, startsWith("AuthMe-CacheLoader"));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldCreateExecutorWhenVirtualThreadsAreDisabled() {
        ListeningExecutorService executor =
            CacheDataSource.createExecutorService(false, Executors.class);
        assertThat(executor, notNullValue());
        executor.shutdownNow();
    }

    @Test
    void shouldConstructCacheDataSource() {
        CacheDataSource dataSource = new CacheDataSource(
            mock(DataSource.class), mock(PlayerCache.class));
        assertThat(dataSource, notNullValue());
        dataSource.closeConnection();
    }

    private static final class NoVirtualThreads {
    }
}
