package fr.xephi.authme.datasource;

import com.zaxxer.hikari.HikariDataSource;
import fr.xephi.authme.settings.Settings;

/**
 * Integration test for {@link MySQL}.
 */
class MySqlIntegrationTest extends AbstractH2DataSourceIntegrationTest {

    @Override
    protected DataSource createDataSource(Settings settings, HikariDataSource hikariSource) {
        return SqlDataSourceTestUtil.createMySql(settings, hikariSource);
    }
}
