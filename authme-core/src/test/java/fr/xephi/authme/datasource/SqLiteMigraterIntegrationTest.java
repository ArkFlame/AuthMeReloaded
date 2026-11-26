package fr.xephi.authme.datasource;

import com.google.common.collect.Lists;
import com.google.common.io.Files;
import fr.xephi.authme.TestHelper;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.settings.Settings;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Stream;

import static fr.xephi.authme.AuthMeMatchers.hasAuthBasicData;
import static fr.xephi.authme.AuthMeMatchers.hasAuthLocation;
import static fr.xephi.authme.datasource.SqlDataSourceTestUtil.createSqlite;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.mock;

/**
 * Integration test for {@link SqLiteMigrater}. Uses a real SQLite database.
 */
class SqLiteMigraterIntegrationTest {

    @TempDir
    static File dataFolder;
    private static SQLite sqLite;

    @BeforeAll
    static void setup() throws SQLException, IOException {
        TestHelper.setupLogger();

        Settings settings = mock(Settings.class);
        TestHelper.returnDefaultsForAllProperties(settings);

        File sqliteDbFile = TestHelper.getJarFile(TestHelper.PROJECT_ROOT + "datasource/sqlite.april2016.db");
        File tempFile = new File(dataFolder, "authme.db");
        Files.copy(sqliteDbFile, tempFile);

        Connection con = DriverManager.getConnection("jdbc:sqlite:" + tempFile.getPath());
        sqLite = createSqlite(settings, dataFolder, con);
        sqLite.setup();
        sqLite.migrateIfNeeded();
    }

    @AfterAll
    static void close() {
        sqLite.closeConnection();
    }

    @Test
    void shouldRun() {
        List<PlayerAuth> auths = sqLite.getAllAuths();
        assertThat(Lists.transform(auths, PlayerAuth::getNickname),
            containsInAnyOrder("mysql1", "mysql2", "mysql3", "mysql4", "mysql5", "mysql6"));

        // Check that backup was made
        File backupsFolder = new File(dataFolder, "backups");
        assertThat(backupsFolder.exists(), equalTo(true));
        assertThat(backupsFolder.isDirectory(), equalTo(true));
        assertThat(backupsFolder.list(), arrayContaining(containsString("authme")));
    }

    @ParameterizedTest(name = "migrates {0}")
    @MethodSource("migratedAccounts")
    void shouldMigrateAccount(String name, String realName, String email, String ip,
                              double x, double y, double z, String world, long lastLogin) {
        PlayerAuth auth = getByNameOrFail(name, sqLite.getAllAuths());
        assertThat(auth, hasAuthBasicData(name, realName, email, ip));
        assertThat(auth, hasAuthLocation(x, y, z, world, 0, 0));
        assertThat(auth.getLastLogin(), equalTo(lastLogin));
    }

    private static Stream<Arguments> migratedAccounts() {
        return Stream.of(
            Arguments.of("mysql1", "mysql1", "user1@example.com", "192.168.4.41", 0.0, 0.0, 0.0,
                "world1", 1472992664137L),
            Arguments.of("mysql2", "Player", "user2@example.com", null, 0.0, 0.0, 0.0, "world2", 1472992668391L),
            Arguments.of("mysql3", "mysql3", null, "132.54.76.98", 0.0, 0.0, 0.0, "world3", 1472992672790L),
            Arguments.of("mysql4", "MySQL4", null, null, 25.0, 4.0, 17.0, "world4", 1472992676790L),
            Arguments.of("mysql5", "mysql5", null, null, 0.0, 0.0, 0.0, "world5", 1472992680922L),
            Arguments.of("mysql6", "MySql6", "user6@example.com", "44.45.67.188",
                28.5, 53.43, -147.23, "world6", 1472992686300L));
    }

    private static PlayerAuth getByNameOrFail(String name, List<PlayerAuth> auths) {
        return auths.stream()
            .filter(auth -> name.equals(auth.getNickname()))
            .findFirst().orElseThrow(() -> new IllegalStateException("No PlayerAuth with name '" + name + "'"));
    }
}
