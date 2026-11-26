package fr.xephi.authme.datasource;

import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.data.auth.PlayerCache;
import fr.xephi.authme.security.crypts.HashedPassword;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
        PlayerAuth stored = auth("playerone");
        PlayerAuth changed = auth("playerone");
        when(source.getAuth("playerone")).thenReturn(stored, changed);
        assertThat(dataSource.getAuth("PlayerOne"), sameInstance(stored));

        when(source.updatePassword(eq("playerone"), any(HashedPassword.class))).thenReturn(false);
        boolean result = dataSource.updatePassword("PlayerOne", new HashedPassword("hash", "salt"));

        assertThat(result, is(false));
        assertThat(dataSource.getCachedAuths().getIfPresent("playerone"), nullValue());
        assertThat(dataSource.getAuth("PlayerOne"), sameInstance(changed));
        verify(source, times(2)).getAuth("playerone");
    }

    @Test
    void shouldInvalidateEntryWhenRemoveAuthFails() {
        PlayerAuth stored = auth("playerone");
        when(source.getAuth("playerone")).thenReturn(stored);
        assertThat(dataSource.getAuth("PlayerOne"), sameInstance(stored));

        when(source.removeAuth("playerone")).thenReturn(false);
        dataSource.removeAuth("PlayerOne");

        assertThat(dataSource.getCachedAuths().getIfPresent("playerone"), nullValue());
    }

    @Test
    void shouldRemoveEntryOnSuccessfulRemoveAuth() {
        PlayerAuth stored = auth("playerone");
        when(source.getAuth("playerone")).thenReturn(stored);
        assertThat(dataSource.getAuth("PlayerOne"), sameInstance(stored));

        when(source.removeAuth("playerone")).thenReturn(true);
        assertThat(dataSource.removeAuth("PlayerOne"), is(true));

        assertThat(dataSource.getCachedAuths().getIfPresent("playerone"), nullValue());
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
    void shouldInvalidateNormalizedNamesOnPurge() {
        PlayerAuth stored = auth("playerone");
        when(source.getAuth("playerone")).thenReturn(stored);
        assertThat(dataSource.getAuth("PlayerOne"), sameInstance(stored));

        dataSource.purgeRecords(Collections.singletonList("PlayerOne"));

        assertThat(dataSource.getCachedAuths().getIfPresent("playerone"), nullValue());
    }

    @Test
    void shouldNormalizeNameOnExplicitInvalidation() {
        PlayerAuth stored = auth("playerone");
        when(source.getAuth("playerone")).thenReturn(stored);
        assertThat(dataSource.getAuth("PlayerOne"), sameInstance(stored));

        dataSource.invalidateCache("PlayerOne");

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
