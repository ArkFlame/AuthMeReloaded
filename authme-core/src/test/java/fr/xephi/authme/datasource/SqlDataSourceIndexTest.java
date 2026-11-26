package fr.xephi.authme.datasource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks, against a real database, the metadata lookup that decides whether the last IP column still
 * needs an index. Lookups by that column run on every login, so a missing index means a table scan each time.
 */
class SqlDataSourceIndexTest {

    private Connection connection;

    @BeforeEach
    void createTable() throws SQLException {
        connection = DriverManager.getConnection("jdbc:h2:mem:index_test;DB_CLOSE_DELAY=-1", "sa", "sa");
        try (Statement st = connection.createStatement()) {
            st.execute("DROP TABLE IF EXISTS authme");
            st.execute("CREATE TABLE authme (id INT PRIMARY KEY, username VARCHAR(255) NOT NULL UNIQUE, "
                + "ip VARCHAR(40))");
        }
    }

    @AfterEach
    void closeConnection() throws SQLException {
        connection.close();
    }

    @Test
    void shouldReportOnlyTheColumnsCoveredByAnIndex() throws SQLException {
        assertTrue(SqlDataSourceUtils.isColumnIndexed(connection.getMetaData(), null, "AUTHME", "id"));
        assertTrue(SqlDataSourceUtils.isColumnIndexed(connection.getMetaData(), null, "AUTHME", "USERNAME"));
        assertFalse(SqlDataSourceUtils.isColumnIndexed(connection.getMetaData(), null, "AUTHME", "ip"));
    }

    @Test
    void shouldSeeTheIndexOnceItIsCreated() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.executeUpdate("CREATE INDEX idx_authme_ip ON authme (ip)");
        }

        assertTrue(SqlDataSourceUtils.isColumnIndexed(connection.getMetaData(), null, "AUTHME", "ip"));
    }
}
