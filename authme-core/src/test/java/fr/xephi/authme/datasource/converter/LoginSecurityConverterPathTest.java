package fr.xephi.authme.datasource.converter;

import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.settings.Settings;
import fr.xephi.authme.settings.properties.ConverterSettings;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/** Ensures the LoginSecurity converter opens the SQLite file whose existence it checked. */
@ExtendWith(MockitoExtension.class)
class LoginSecurityConverterPathTest {

    @Mock
    private DataSource dataSource;

    @Mock
    private Settings settings;

    @TempDir
    private File tempFolder;

    @Test
    void shouldOpenTheCheckedSqliteFile() throws IOException {
        given(settings.getProperty(ConverterSettings.LOGINSECURITY_USE_SQLITE)).willReturn(true);
        given(settings.getProperty(ConverterSettings.LOGINSECURITY_MYSQL_HOST)).willReturn("");
        given(settings.getProperty(ConverterSettings.LOGINSECURITY_MYSQL_DATABASE)).willReturn("");
        given(settings.getProperty(ConverterSettings.LOGINSECURITY_MYSQL_USER)).willReturn("");
        given(settings.getProperty(ConverterSettings.LOGINSECURITY_MYSQL_PASSWORD)).willReturn("");

        File dbFile = new File(tempFolder, "LoginSecurity/LoginSecurity.db");
        assertTrue(dbFile.getParentFile().isDirectory() || dbFile.getParentFile().mkdirs());
        assertTrue(dbFile.createNewFile());

        LoginSecurityConverter converter = spy(new LoginSecurityConverter(
            new File(tempFolder, "AuthMe"), dataSource, settings));
        doReturn(null).when(converter).createSqliteConnection(anyString());

        converter.execute(mock(CommandSender.class));

        verify(converter).createSqliteConnection(dbFile.getAbsolutePath());
    }
}
