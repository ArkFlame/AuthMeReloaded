package fr.xephi.authme.datasource.converter;

import com.google.common.io.Files;
import fr.xephi.authme.TestHelper;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.util.FileUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockMakers;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static fr.xephi.authme.AuthMeMatchers.equalToHash;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.withSettings;
import static org.mockito.hamcrest.MockitoHamcrest.argThat;

/**
 * Test for {@link AuthPlusConverter}.
 */
@ExtendWith(MockitoExtension.class)
class AuthPlusConverterTest extends AbstractConverterTest {

    private static final UUID UUID_TESTPLAYER    = UUID.fromString("a811b264-a1da-42dd-b1b7-b601234b7cc7");
    private static final UUID UUID_EXISTINGPLAYER = UUID.fromString("b922c375-b2eb-53ee-c2c8-c712345c8dd8");
    private static final UUID UUID_UNKNOWNPLAYER  = UUID.fromString("cc033486-c3fc-64ff-d3d9-d823456d9ee9");

    private AuthPlusConverter converter;

    @Mock
    private DataSource dataSource;

    @Mock
    private CommandSender sender;

    @TempDir
    File tempFolder;

    @Captor
    private ArgumentCaptor<PlayerAuth> authCaptor;

    @BeforeEach
    void setUpConverter() {
        File authMeDataFolder = new File(tempFolder, "AuthMe");
        authMeDataFolder.mkdir();
        converter = new AuthPlusConverter(authMeDataFolder, dataSource);
        new File(tempFolder, "Auth").mkdir();
    }

    @Test
    void shouldImportAndSkipAccordingly() throws IOException {
        File playersYml = new File(tempFolder, "Auth/players.yml");
        FileUtils.create(playersYml);
        Files.copy(TestHelper.getJarFile("datasource/Auth/players.yml"), playersYml);

        given(dataSource.isAuthAvailable("existingplayer")).willReturn(true);
        given(dataSource.isAuthAvailable("testplayer")).willReturn(false);
        given(dataSource.saveAuth(any(PlayerAuth.class))).willReturn(true);

        OfflinePlayer testPlayer    = mockOfflinePlayer("TestPlayer");
        OfflinePlayer existingPlayer = mockOfflinePlayer("ExistingPlayer");
        // UUID_UNKNOWNPLAYER resolves to null (player never joined)
        OfflinePlayer unknownPlayer  = mockOfflinePlayer(null);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class, withSettings().mockMaker(MockMakers.INLINE))) {
            bukkit.when(() -> Bukkit.getOfflinePlayer(UUID_TESTPLAYER)).thenReturn(testPlayer);
            bukkit.when(() -> Bukkit.getOfflinePlayer(UUID_EXISTINGPLAYER)).thenReturn(existingPlayer);
            bukkit.when(() -> Bukkit.getOfflinePlayer(UUID_UNKNOWNPLAYER)).thenReturn(unknownPlayer);

            converter.execute(sender);
        }

        verify(dataSource).saveAuth(authCaptor.capture());
        List<PlayerAuth> saved = authCaptor.getAllValues();
        assertThat(saved, hasSize(1));
        assertThat(saved.get(0).getNickname(), equalTo("testplayer"));
        assertThat(saved.get(0).getRealName(), equalTo("TestPlayer"));
        assertThat(saved.get(0).getUuid(), equalTo(UUID_TESTPLAYER));
        assertThat(saved.get(0).getPassword(),
            equalToHash("pbkdf2$120000$JddgMm9rNhbYNfEmf4pOKA==$RcAMtgm/KnKFxfNOpg95tb7s5OzB2Fv4Wj1HOAI/TWY="));
        verify(sender).sendMessage(argThat(containsString("1 account(s) imported")));
    }

    @Test
    void shouldReportMissingFile() {
        converter.execute(sender);

        verifyNoInteractions(dataSource);
        verify(sender).sendMessage(argThat(containsString("does not exist")));
    }
}
