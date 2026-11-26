package fr.xephi.authme.datasource;

import com.zaxxer.hikari.HikariDataSource;
import fr.xephi.authme.TestHelper;
import fr.xephi.authme.settings.Settings;
import fr.xephi.authme.settings.properties.DatabaseSettings;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * Test for the PostgreSQL connection pool's maximum lifetime configuration.
 */
class PostgreSqlMaxLifetimeTest {

    @Test
    void shouldSetMaxLifetimeWithoutIntegerOverflow() {
        TestHelper.setupLogger();
        Settings settings = mock(Settings.class);
        TestHelper.returnDefaultsForAllProperties(settings);
        given(settings.getProperty(DatabaseSettings.MYSQL_CONNECTION_MAX_LIFETIME)).willReturn(3_000_000);

        PostgreSqlDataSource dataSource =
            SqlDataSourceTestUtil.createPostgres(settings, mock(HikariDataSource.class));
        dataSource.reload();

        assertThat(dataSource.ds.getMaxLifetime(), is(3_000_000L * 1000L));
    }
}
