package fr.xephi.authme.datasource.converter;

import fr.xephi.authme.AuthMe;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.util.FileUtils;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;

import static fr.xephi.authme.AuthMeMatchers.equalToHash;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Test for {@link RoyalAuthConverter}. The RoyalAuth user data folder is redirected to a temporary folder.
 */
@ExtendWith(MockitoExtension.class)
class RoyalAuthConverterTest extends AbstractConverterTest {

    @Mock
    private AuthMe plugin;

    @Mock
    private Server server;

    @Mock
    private DataSource dataSource;

    @TempDir
    File tempFolder;

    @Captor
    private ArgumentCaptor<PlayerAuth> authCaptor;

    private RoyalAuthConverter converter;

    @BeforeEach
    void setUpConverter() {
        given(plugin.getServer()).willReturn(server);
        converter = new RoyalAuthConverter(plugin, dataSource);
    }

    @Test
    void shouldImportAccountsAndSkipPlayersWithoutData() throws IOException {
        OfflinePlayer testPlayer = mockOfflinePlayer("TestPlayer");
        OfflinePlayer existingPlayer = mockOfflinePlayer("ExistingPlayer");
        OfflinePlayer playerWithoutFile = mockOfflinePlayer("NoFilePlayer");
        given(server.getOfflinePlayers()).willReturn(
            new OfflinePlayer[]{testPlayer, existingPlayer, playerWithoutFile});
        given(dataSource.isAuthAvailable("testplayer")).willReturn(false);
        given(dataSource.isAuthAvailable("existingplayer")).willReturn(true);
        given(dataSource.isAuthAvailable("nofileplayer")).willReturn(false);

        File testPlayerFile = new File(tempFolder, "testplayer.yml");
        Files.write(testPlayerFile.toPath(), Arrays.asList(
            "timestamps:", "  quit: 1546300800000", "login:", "  password: royalauth-hash"),
            StandardCharsets.UTF_8);

        try (MockedStatic<FileUtils> fileUtils = mockStatic(FileUtils.class)) {
            mockUserDataPath(fileUtils, "testplayer.yml", testPlayerFile);
            mockUserDataPath(fileUtils, "existingplayer.yml", new File(tempFolder, "existingplayer.yml"));
            mockUserDataPath(fileUtils, "nofileplayer.yml", new File(tempFolder, "nofileplayer.yml"));

            converter.execute(mock(CommandSender.class));
        }

        verify(dataSource).saveAuth(authCaptor.capture());
        PlayerAuth auth = authCaptor.getValue();
        verify(dataSource).updateSession(auth);
        verify(dataSource, times(1)).updateSession(any(PlayerAuth.class));
        assertThat(auth.getNickname(), equalTo("testplayer"));
        assertThat(auth.getRealName(), equalTo("TestPlayer"));
        assertThat(auth.getPassword(), equalToHash("royalauth-hash"));
        assertThat(auth.getLastLogin(), equalTo(1546300800000L));
    }

    private void mockUserDataPath(MockedStatic<FileUtils> fileUtils, String fileName, File resultingFile) {
        fileUtils.when(() -> FileUtils.makePath(".", "plugins", "RoyalAuth", "userdata", fileName))
            .thenReturn(resultingFile.getAbsolutePath());
    }
}
