package fr.xephi.authme.datasource;

import fr.xephi.authme.TestHelper;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.settings.Settings;
import fr.xephi.authme.settings.properties.DatabaseSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static fr.xephi.authme.datasource.SqlDataSourceTestUtil.silentClose;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration test for {@link SQLite}.
 */
class SQLiteIntegrationTest extends AbstractDataSourceIntegrationTest {

    /** Mock of a settings instance. */
    private static Settings settings;
    /** Collection of SQL statements to execute for initialization of a test. */
    private static String[] sqlInitialize;
    /** Connection to the SQLite test database. */
    private Connection con;

    /**
     * Set up the settings mock to return specific values for database settings and load {@link #sqlInitialize}.
     */
    @BeforeAll
    static void initializeSettings() throws IOException, ClassNotFoundException {
        // Check that we have an implementation for SQLite
        Class.forName("org.sqlite.JDBC");

        settings = mock(Settings.class);
        TestHelper.returnDefaultsForAllProperties(settings);
        setProperty(settings, DatabaseSettings.MYSQL_DATABASE, "sqlite-test");
        setProperty(settings, DatabaseSettings.MYSQL_TABLE, "authme");
        setProperty(settings, DatabaseSettings.MYSQL_COL_PLAYER_UUID, "playerUUID");
        TestHelper.setupLogger();

        Path sqlInitFile = TestHelper.getJarPath(TestHelper.PROJECT_ROOT + "datasource/sql-initialize.sql");
        // Note ljacqu 20160221: It appears that we can only run one statement per Statement.execute() so we split
        // the SQL file by ";\n" as to get the individual statements
        sqlInitialize = new String(Files.readAllBytes(sqlInitFile)).split(";(\\r?)\\n");
    }

    @BeforeEach
    void initializeConnectionAndTable() throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (Statement st = connection.createStatement()) {
            st.execute("DROP TABLE IF EXISTS authme");
            for (String statement : sqlInitialize) {
                st.execute(statement);
            }
        }
        con = connection;
    }

    @AfterEach
    void closeConnection() {
        silentClose(con);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("tableSetups")
    void shouldSetUpTableIfMissing(String description, String[] tableStatements) throws SQLException {
        Statement st = con.createStatement();
        // Drop the table; optionally recreate it with only some of the columns first, since SQLite doesn't
        // support ALTER TABLE t DROP COLUMN c. Either way setup() has to leave a fully usable table.
        st.execute("DROP TABLE authme");
        for (String statement : tableStatements) {
            st.execute(statement);
        }
        SQLite sqLite = new SQLite(settings, null, con);

        sqLite.setup();

        // Save some player to verify database is operational
        sqLite.saveAuth(PlayerAuth.builder().name("Name").build());
        assertThat(description, sqLite.getAllAuths(), hasSize(1));
    }

    private static Stream<Arguments> tableSetups() {
        return Stream.of(
            Arguments.of("table absent", new String[0]),
            Arguments.of("missing columns", new String[]{
                "CREATE TABLE authme ("
                    + "id bigint, "
                    + "username varchar(255) unique, "
                    + "password varchar(255) not null, "
                    + "primary key (id));"}));
    }

    @Test
    void shouldReadPlayerUuidFromGetAllAuths() {
        SQLite sqLite = new SQLite(settings, null, con);

        List<PlayerAuth> auths = sqLite.getAllAuths();

        // then - bobby has playerUUID set in test data, user does not
        PlayerAuth bobby = auths.stream().filter(a -> "bobby".equals(a.getNickname())).findFirst().orElse(null);
        assertThat(bobby, notNullValue());
        assertThat(bobby.getUuid(), equalTo(UUID.fromString("a8674b3e-7c1a-4cfa-8f37-1c6e7d2b9f44")));

        PlayerAuth user = auths.stream().filter(a -> "user".equals(a.getNickname())).findFirst().orElse(null);
        assertThat(user, notNullValue());
        assertThat(user.getUuid(), equalTo(null));
    }

    @Override
    protected DataSource getDataSource(String saltColumn) {
        when(settings.getProperty(DatabaseSettings.MYSQL_COL_SALT)).thenReturn(saltColumn);
        return new SQLite(settings, null, con);
    }
}
