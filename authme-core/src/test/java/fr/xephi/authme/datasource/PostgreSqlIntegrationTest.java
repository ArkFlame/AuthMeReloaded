package fr.xephi.authme.datasource;

import com.zaxxer.hikari.HikariDataSource;
import fr.xephi.authme.settings.Settings;

/**
 * Integration test for {@link PostgreSqlDataSource}.
 */
class PostgreSqlIntegrationTest extends AbstractH2DataSourceIntegrationTest {

    @Override
    protected DataSource createDataSource(Settings settings, HikariDataSource hikariSource) {
        return SqlDataSourceTestUtil.createPostgres(settings, hikariSource);
    }
}
