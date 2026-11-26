package fr.xephi.authme.datasource;

import fr.xephi.authme.settings.Settings;

import java.sql.Connection;

/**
 * Resource closing test for {@link PostgreSqlDataSource}.
 */
class PostgreSqlResourceClosingTest extends AbstractSqlDataSourceResourceClosingTest {

    @Override
    protected DataSource createDataSource(Settings settings, Connection connection) throws Exception {
        return new PostgreSqlDataSource(settings, SqlDataSourceTestUtil.mockHikariDataSource(connection),
            SqlDataSourceTestUtil.mockExtensionsFactory());
    }
}
