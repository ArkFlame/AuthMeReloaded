package fr.xephi.authme.datasource;

import fr.xephi.authme.TestHelper;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
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

    @ParameterizedTest(name = "NULLABLE={0} for column {2}")
    @MethodSource("nullableStatusCases")
    void shouldReturnFalseForNullableColumn(int nullableStatus, String tableName, String columnName)
        throws SQLException {
        ResultSet resultSet = mock(ResultSet.class);
        given(resultSet.getInt("NULLABLE")).willReturn(nullableStatus);
        given(resultSet.next()).willReturn(true);
        DatabaseMetaData metaData = mockMetaData(tableName, columnName, resultSet);

        boolean result = SqlDataSourceUtils.isNotNullColumn(metaData, tableName, columnName);

        assertThat(result, equalTo(false));
    }

    private static Stream<Arguments> nullableStatusCases() {
        return Stream.of(
            Arguments.of(DatabaseMetaData.columnNullable, "data", "category"),
            Arguments.of(DatabaseMetaData.columnNullableUnknown, "comments", "author"));
    }

    @ParameterizedTest(name = "{0} throws for unknown column")
    @MethodSource("unknownColumnOperations")
    void shouldThrowForUnknownColumn(String description, ColumnOperation operation) throws SQLException {
        String tableName = "data";
        String columnName = "unknown";
        ResultSet resultSet = mock(ResultSet.class);
        given(resultSet.next()).willReturn(false);
        DatabaseMetaData metaData = mockMetaData(tableName, columnName, resultSet);

        assertThrows(IllegalStateException.class,
            () -> operation.apply(metaData, tableName, columnName));
    }

    private static Stream<Arguments> unknownColumnOperations() {
        return Stream.of(
            Arguments.of("nullable check", (ColumnOperation) SqlDataSourceUtils::isNotNullColumn),
            Arguments.of("default value retrieval", (ColumnOperation) SqlDataSourceUtils::getColumnDefaultValue));
    }

    @ParameterizedTest(name = "COLUMN_DEF={0}")
    @MethodSource("columnDefaultValues")
    void shouldGetColumnDefaultValue(Object columnDefinition, Matcher<Object> expected) throws SQLException {
        String tableName = "data";
        String columnName = "category";
        ResultSet resultSet = mock(ResultSet.class);
        given(resultSet.getObject("COLUMN_DEF")).willReturn(columnDefinition);
        given(resultSet.next()).willReturn(true);
        DatabaseMetaData metaData = mockMetaData(tableName, columnName, resultSet);

        Object defaultValue = SqlDataSourceUtils.getColumnDefaultValue(metaData, tableName, columnName);

        assertThat(defaultValue, expected);
    }

    private static Stream<Arguments> columnDefaultValues() {
        return Stream.of(
            Arguments.of("Literature", Matchers.<Object>equalTo("Literature")),
            Arguments.of(null, Matchers.<Object>nullValue()));
    }

    private static DatabaseMetaData mockMetaData(String tableName, String columnName, ResultSet resultSet)
        throws SQLException {
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        given(metaData.getColumns(null, null, tableName, columnName)).willReturn(resultSet);
        return metaData;
    }

    @FunctionalInterface
    private interface ColumnOperation {
        void apply(DatabaseMetaData metaData, String tableName, String columnName) throws SQLException;
    }
}
