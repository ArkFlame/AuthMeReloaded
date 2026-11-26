package fr.xephi.authme.datasource;

import fr.xephi.authme.TestHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Logger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.hamcrest.MockitoHamcrest.argThat;

/**
 * Test for {@link SqlDataSourceUtils}.
 */
class SqlDataSourceUtilsTest {

    private Logger logger;

    @BeforeEach
    void initLogger() {
        logger = TestHelper.setupLogger();
    }

    @Test
    void shouldLogException() {
        String msg = "Hocus pocus did not work";
        SQLException ex = new SQLException(msg);

        SqlDataSourceUtils.logSqlException(ex);

        verify(logger).warning(argThat(containsString(msg)));
    }

    @Test
    void shouldFetchNullableStatus() throws SQLException {
        String tableName = "data";
        String columnName = "category";
        ResultSet resultSet = mock(ResultSet.class);
        given(resultSet.getInt("NULLABLE")).willReturn(DatabaseMetaData.columnNullable);
        given(resultSet.next()).willReturn(true);
        DatabaseMetaData metaData = mockMetaData(tableName, columnName, resultSet);

        boolean result = SqlDataSourceUtils.isNotNullColumn(metaData, tableName, columnName);

        assertThat(result, equalTo(false));
    }

    @Test
    void shouldReturnFalseForUnknownNullableStatus() throws SQLException {
        String tableName = "comments";
        String columnName = "author";
        ResultSet resultSet = mock(ResultSet.class);
        given(resultSet.getInt("NULLABLE")).willReturn(DatabaseMetaData.columnNullableUnknown);
        given(resultSet.next()).willReturn(true);
        DatabaseMetaData metaData = mockMetaData(tableName, columnName, resultSet);

        boolean result = SqlDataSourceUtils.isNotNullColumn(metaData, tableName, columnName);

        assertThat(result, equalTo(false));
    }

    @Test
    void shouldThrowForUnknownColumnInNullableCheck() throws SQLException {
        String tableName = "data";
        String columnName = "unknown";
        ResultSet resultSet = mock(ResultSet.class);
        given(resultSet.next()).willReturn(false);
        DatabaseMetaData metaData = mockMetaData(tableName, columnName, resultSet);

        assertThrows(IllegalStateException.class,
            () -> SqlDataSourceUtils.isNotNullColumn(metaData, tableName, columnName));
    }

    @Test
    void shouldGetDefaultValue() throws SQLException {
        String tableName = "data";
        String columnName = "category";
        ResultSet resultSet = mock(ResultSet.class);
        given(resultSet.getObject("COLUMN_DEF")).willReturn("Literature");
        given(resultSet.next()).willReturn(true);
        DatabaseMetaData metaData = mockMetaData(tableName, columnName, resultSet);

        Object defaultValue = SqlDataSourceUtils.getColumnDefaultValue(metaData, tableName, columnName);

        assertThat(defaultValue, equalTo("Literature"));
    }

    @Test
    void shouldThrowForUnknownColumnInDefaultValueRetrieval() throws SQLException {
        String tableName = "data";
        String columnName = "unknown";
        ResultSet resultSet = mock(ResultSet.class);
        given(resultSet.next()).willReturn(false);
        DatabaseMetaData metaData = mockMetaData(tableName, columnName, resultSet);

        assertThrows(IllegalStateException.class,
            () -> SqlDataSourceUtils.getColumnDefaultValue(metaData, tableName, columnName));
    }

    @Test
    void shouldHandleNullDefaultValue() throws SQLException {
        String tableName = "data";
        String columnName = "category";
        ResultSet resultSet = mock(ResultSet.class);
        given(resultSet.getObject("COLUMN_DEF")).willReturn(null);
        given(resultSet.next()).willReturn(true);
        DatabaseMetaData metaData = mockMetaData(tableName, columnName, resultSet);

        Object defaultValue = SqlDataSourceUtils.getColumnDefaultValue(metaData, tableName, columnName);

        assertThat(defaultValue, nullValue());
    }

    private static DatabaseMetaData mockMetaData(String tableName, String columnName, ResultSet resultSet)
        throws SQLException {
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        given(metaData.getColumns(null, null, tableName, columnName)).willReturn(resultSet);
        return metaData;
    }
}
