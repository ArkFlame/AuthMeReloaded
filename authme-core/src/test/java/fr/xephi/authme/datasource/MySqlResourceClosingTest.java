package fr.xephi.authme.datasource;

import fr.xephi.authme.settings.Settings;

import java.sql.Connection;

/**
 * Resource closing test for {@link MySQL}.
 */
class MySqlResourceClosingTest extends AbstractSqlDataSourceResourceClosingTest {

    @Override
    protected DataSource createDataSource(Settings settings, Connection connection) throws Exception {
        return new MySQL(settings, SqlDataSourceTestUtil.mockHikariDataSource(connection),
            SqlDataSourceTestUtil.mockExtensionsFactory());
    }

}
