package fr.xephi.authme.datasource;

import fr.xephi.authme.data.auth.PlayerAuth;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Tests the batched record deletion performed by {@link AbstractSqlDataSource}.
 */
class AbstractSqlDataSourceDeleteRecordsTest {

    private static final String SQL = "DELETE FROM users WHERE name=?;";

    @Test
    void shouldNotQueryDatabaseWhenNothingToDelete() throws SQLException {
        // given
        Connection connection = mock(Connection.class);
        AbstractSqlDataSource dataSource = new StubDataSource();

        // when
        dataSource.deleteRecords(connection, SQL, Collections.emptyList());

        // then
        verifyNoInteractions(connection);
    }

    @Test
    void shouldBatchDeleteLowercasedNamesInOneStatement() throws SQLException {
        // given
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        given(connection.prepareStatement(SQL)).willReturn(statement);
        AbstractSqlDataSource dataSource = new StubDataSource();

        // when
        dataSource.deleteRecords(connection, SQL, Arrays.asList("Alice", "BOB"));

        // then
        verify(statement).setString(1, "alice");
        verify(statement).setString(1, "bob");
        verify(statement, times(2)).addBatch();
        verify(statement).executeBatch();
    }

    private static final class StubDataSource extends AbstractSqlDataSource {

        @Override
        protected boolean isColumnMissing(DatabaseMetaData metaData, String columnName) {
            throw new UnsupportedOperationException();
        }

        @Override
        String getJdbcUrl(String host, String port, String database) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PlayerAuth getAuth(String user) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Set<String> getRecordsToPurge(long until) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void purgeRecords(Collection<String> toPurge) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean removeAuth(String user) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void closeConnection() {
            throw new UnsupportedOperationException();
        }

        @Override
        public DataSourceType getType() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<String> getLoggedPlayersWithEmptyMail() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<PlayerAuth> getAllAuths() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<PlayerAuth> getRecentlyLoggedInPlayers() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean setTotpKey(String user, String totpKey) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void reload() {
            throw new UnsupportedOperationException();
        }
    }
}
