package fr.xephi.authme.datasource;

import org.junit.jupiter.api.Test;
import org.mockito.Answers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;

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

    // Only deleteRecords() is exercised, so real methods run and inherited abstract ones are never called.
    private final AbstractSqlDataSource dataSource =
        mock(AbstractSqlDataSource.class, Answers.CALLS_REAL_METHODS);
    private final Connection connection = mock(Connection.class);

    @Test
    void shouldNotQueryDatabaseWhenNothingToDelete() throws SQLException {
        // when
        dataSource.deleteRecords(connection, SQL, Collections.emptyList());

        // then
        verifyNoInteractions(connection);
    }

    @Test
    void shouldBatchDeleteLowercasedNamesInOneStatement() throws SQLException {
        // given
        PreparedStatement statement = mock(PreparedStatement.class);
        given(connection.prepareStatement(SQL)).willReturn(statement);

        // when
        dataSource.deleteRecords(connection, SQL, Arrays.asList("Alice", "BOB"));

        // then
        verify(statement).setString(1, "alice");
        verify(statement).setString(1, "bob");
        verify(statement, times(2)).addBatch();
        verify(statement).executeBatch();
    }
}
