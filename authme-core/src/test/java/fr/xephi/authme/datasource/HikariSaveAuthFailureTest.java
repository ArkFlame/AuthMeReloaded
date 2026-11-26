package fr.xephi.authme.datasource;

import fr.xephi.authme.data.auth.PlayerAuth;
import org.mockito.MockMakers;
import fr.xephi.authme.datasource.columnshandler.AuthMeColumnsHandler;
import fr.xephi.authme.datasource.columnshandler.PlayerAuthColumn;
import fr.xephi.authme.datasource.mysqlextensions.MySqlExtension;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.Collections;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Verifies that a failed primary INSERT is not masked by the Hikari-specific follow-up statements.
 */
class HikariSaveAuthFailureTest {

    @Test
    void shouldNotReportSuccessWhenPrimaryInsertFails() {
        AuthMeColumnsHandler columnsHandler = mock(AuthMeColumnsHandler.class, withSettings().mockMaker(MockMakers.INLINE));
        given(columnsHandler.insert(any(PlayerAuth.class), any(PlayerAuthColumn[].class))).willReturn(false);
        MySqlExtension extension = mock(MySqlExtension.class, withSettings().mockMaker(MockMakers.INLINE));
        Connection connection = mock(Connection.class, withSettings().mockMaker(MockMakers.INLINE));

        AbstractHikariSqlDataSource dataSource = new HikariStub(columnsHandler, extension, connection);

        assertThat(dataSource.saveAuth(PlayerAuth.builder().name("Test").build()), is(false));
        verifyNoInteractions(extension);
        verifyNoInteractions(connection);
    }

    private static class HikariStub extends AbstractHikariSqlDataSource {
        private final Connection connection;

        HikariStub(AuthMeColumnsHandler columnsHandler, MySqlExtension extension, Connection connection) {
            this.columnsHandler = columnsHandler;
            this.sqlExtension = extension;
            this.columnOthers = Collections.emptyList();
            this.connection = connection;
        }

        @Override
        public Connection getConnection() {
            return connection;
        }

        @Override
        public DataSourceType getType() {
            return DataSourceType.MYSQL;
        }

        @Override
        public void reload() {
            // Not needed for this test.
        }

        @Override
        protected boolean isColumnMissing(DatabaseMetaData metaData, String columnName) {
            return false;
        }

        @Override
        String getJdbcUrl(String host, String port, String database) {
            return "";
        }
    }
}
