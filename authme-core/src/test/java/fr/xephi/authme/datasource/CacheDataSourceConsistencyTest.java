package fr.xephi.authme.datasource;

import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.data.auth.PlayerCache;
import fr.xephi.authme.security.crypts.HashedPassword;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Collections;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests that all write paths of {@link CacheDataSource} keep the cache consistent with the
 * underlying data source, including with mixed-case player names and failed writes.
 */
class CacheDataSourceConsistencyTest {

    private DataSource source;
    private CacheDataSource dataSource;

    @BeforeEach
    void setUp() {
        source = mock(DataSource.class);
        dataSource = new CacheDataSource(source, mock(PlayerCache.class));
    }

    @AfterEach
    void tearDown() {
        dataSource.closeConnection();
    }

    @Test
    void shouldInvalidateEntryWhenUpdatePasswordFails() {
        PlayerAuth changed = auth("playerone");
        seedCache(changed);

        when(source.updatePassword(eq("playerone"), any(HashedPassword.class))).thenReturn(false);
        boolean result = dataSource.updatePassword("PlayerOne", new HashedPassword("hash", "salt"));

        assertThat(result, is(false));
        assertCacheInvalidated();
        assertThat(dataSource.getAuth("PlayerOne"), sameInstance(changed));
        verify(source, times(2)).getAuth("playerone");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldReloadAfterUpdateSessionOnlyForForeignInstance(boolean sameInstance) {
        PlayerAuth stored = auth("playerone");
        when(source.getAuth("playerone")).thenReturn(stored);
        assertThat(dataSource.getAuth("PlayerOne"), sameInstance(stored));
        PlayerAuth written = sameInstance ? stored : auth("playerone");
        when(source.updateSession(written)).thenReturn(true);

        assertThat(dataSource.updateSession(written), is(true));
        dataSource.closeConnection(); // waits for any asynchronous reload

        verify(source, times(sameInstance ? 1 : 2)).getAuth("playerone");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldInvalidateEntryOnRemoveAuthRegardlessOfOutcome(boolean success) {
        seedCache();

        when(source.removeAuth("playerone")).thenReturn(success);

        assertThat(dataSource.removeAuth("PlayerOne"), is(success));
        assertCacheInvalidated();
    }

    @Test
    void shouldCachePremiumUuidUnderNormalizedKey() {
        PlayerAuth auth = auth("PlayerOne");
        when(source.updatePremiumUuid(auth)).thenReturn(true);

        assertThat(dataSource.updatePremiumUuid(auth), is(true));

        assertThat(dataSource.getCachedAuths().getIfPresent("playerone"), notNullValue());
        assertThat(dataSource.getCachedAuths().getIfPresent("PlayerOne"), nullValue());
    }

    @Test
    void shouldInvalidateNormalizedNamesOnPurgeAndExplicitInvalidation() {
        seedCache();
        dataSource.purgeRecords(Collections.singletonList("PlayerOne"));
        assertCacheInvalidated();

        seedCache();
        dataSource.invalidateCache("PlayerOne");
        assertCacheInvalidated();
    }

    /**
     * Seeds the cache with a stored auth for "playerone" fetched via "PlayerOne" and asserts it was cached.
     * Extra arguments are returned by subsequent lookups of the same key.
     */
    private void seedCache(PlayerAuth... subsequentLookups) {
        PlayerAuth stored = auth("playerone");
        when(source.getAuth("playerone")).thenReturn(stored, subsequentLookups);
        assertThat(dataSource.getAuth("PlayerOne"), sameInstance(stored));
    }

    private void assertCacheInvalidated() {
        assertThat(dataSource.getCachedAuths().getIfPresent("playerone"), nullValue());
    }

    private static PlayerAuth auth(String name) {
        return PlayerAuth.builder()
            .name(name)
            .realName(name)
            .password(new HashedPassword("hash", "salt"))
            .build();
    }
}
