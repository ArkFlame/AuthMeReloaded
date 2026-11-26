package fr.xephi.authme.datasource.converter;

import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.settings.Settings;
import fr.xephi.authme.settings.properties.ConverterSettings;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static fr.xephi.authme.AuthMeMatchers.equalToHash;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.hamcrest.MockitoHamcrest.argThat;

/**
 * Test for {@link LoginSecurityConverter}. Uses a temporary SQLite file; no external database is needed.
 */
@ExtendWith(MockitoExtension.class)
class LoginSecurityConverterTest extends AbstractConverterTest {

    private static final String PLAYER_UUID = "a811b264-a1da-42dd-b1b7-b601234b7cc7";

    @Mock
    private DataSource dataSource;

    @Mock
    private Settings settings;

    @TempDir
    File tempFolder;

    @Captor
    private ArgumentCaptor<PlayerAuth> authCaptor;

    private LoginSecurityConverter converter;

    @BeforeEach
    void setUpConverter() {
        given(settings.getProperty(ConverterSettings.LOGINSECURITY_USE_SQLITE)).willReturn(true);
        given(settings.getProperty(ConverterSettings.LOGINSECURITY_MYSQL_HOST)).willReturn("");
        given(settings.getProperty(ConverterSettings.LOGINSECURITY_MYSQL_DATABASE)).willReturn("");
        given(settings.getProperty(ConverterSettings.LOGINSECURITY_MYSQL_USER)).willReturn("");
        given(settings.getProperty(ConverterSettings.LOGINSECURITY_MYSQL_PASSWORD)).willReturn("");
        converter = new LoginSecurityConverter(new File(tempFolder, "AuthMe"), dataSource, settings);
    }

    @Test
    void shouldMigrateAccountsAndSkipExistingOnes() throws SQLException, ClassNotFoundException {
        Class.forName("org.sqlite.JDBC");
        File dbFile = new File(tempFolder, "LoginSecurity.db");
        try (Connection setup = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
             Statement statement = setup.createStatement()) {
            statement.execute("CREATE TABLE ls_players (id INTEGER PRIMARY KEY, last_name TEXT, password TEXT, "
                + "last_login TIMESTAMP, registration_date DATE, unique_user_id TEXT, ip_address TEXT)");
            statement.execute("CREATE TABLE ls_locations (id INTEGER PRIMARY KEY, x DOUBLE, y DOUBLE, z DOUBLE, "
                + "world TEXT, yaw DOUBLE, pitch DOUBLE)");
            statement.execute("INSERT INTO ls_players VALUES (1, 'TestPlayer', 'bcrypt-hash', "
                + "'2020-01-02 03:04:05.000', '2019-01-02 00:00:00.000', '" + PLAYER_UUID
                + "', '127.0.0.1')");
            statement.execute("INSERT INTO ls_players VALUES (2, 'ExistingPlayer', 'other-hash', "
                + "NULL, NULL, NULL, NULL)");
            statement.execute("INSERT INTO ls_locations VALUES (1, 1.5, 2.5, 3.5, 'world', 90.0, 45.0)");
            statement.execute("INSERT INTO ls_locations VALUES (2, 0, 0, 0, 'world', 0, 0)");
        }
        given(dataSource.isAuthAvailable("TestPlayer")).willReturn(false);
        given(dataSource.isAuthAvailable("ExistingPlayer")).willReturn(true);
        CommandSender sender = mock(CommandSender.class);

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath())) {
            converter.performConversion(sender, connection);
        }

        verify(dataSource).saveAuth(authCaptor.capture());
        PlayerAuth auth = authCaptor.getValue();
        verify(dataSource).updateSession(auth);
        assertThat(auth.getNickname(), equalTo("testplayer"));
        assertThat(auth.getRealName(), equalTo("TestPlayer"));
        assertThat(auth.getPassword(), equalToHash("bcrypt-hash"));
        assertThat(auth.getLastIp(), equalTo("127.0.0.1"));
        assertThat(auth.getLastLogin(), notNullValue());
        assertThat(auth.getRegistrationDate(), greaterThan(0L));
        assertThat(auth.getUuid(), equalTo(UUID.fromString(PLAYER_UUID)));
        assertThat(auth.getQuitLocX(), equalTo(1.5));
        assertThat(auth.getQuitLocY(), equalTo(2.5));
        assertThat(auth.getQuitLocZ(), equalTo(3.5));
        assertThat(auth.getWorld(), equalTo("world"));
        assertThat(auth.getYaw(), equalTo(90.0F));
        assertThat(auth.getPitch(), equalTo(45.0F));
    }

    @Test
    void shouldInformSenderIfDatabaseFileIsMissing() {
        CommandSender sender = mock(CommandSender.class);

        converter.execute(sender);

        verify(sender).sendMessage(argThat(containsString("does not exist")));
        verifyNoInteractions(dataSource);
    }
}
