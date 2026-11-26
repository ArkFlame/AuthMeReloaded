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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import static fr.xephi.authme.AuthMeMatchers.equalToHash;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.hamcrest.MockitoHamcrest.argThat;

/**
 * Test for {@link CrazyLoginConverter}.
 */
@ExtendWith(MockitoExtension.class)
class CrazyLoginConverterTest extends AbstractConverterTest {

    @Mock
    private DataSource dataSource;

    @Mock
    private Settings settings;

    @TempDir
    File tempFolder;

    @Captor
    private ArgumentCaptor<PlayerAuth> authCaptor;

    private CrazyLoginConverter converter;

    @BeforeEach
    void setUpConverter() {
        converter = new CrazyLoginConverter(tempFolder, dataSource, settings);
    }

    @Test
    void shouldImportAccountsFromPipeSeparatedFile() throws IOException {
        given(settings.getProperty(ConverterSettings.CRAZYLOGIN_FILE_NAME)).willReturn("accounts.db");
        Files.write(new File(tempFolder, "accounts.db").toPath(), Arrays.asList(
            "name|password|salt", "TestPlayer|plain-text-password|extra", "SecondPlayer|other-password|extra",
            "lineWithoutSeparator"), StandardCharsets.UTF_8);
        CommandSender sender = mock(CommandSender.class);

        converter.execute(sender);

        verify(dataSource, times(2)).saveAuth(authCaptor.capture());
        List<PlayerAuth> saved = authCaptor.getAllValues();
        assertThat(saved, hasSize(2));
        assertThat(saved.get(0).getNickname(), equalTo("testplayer"));
        assertThat(saved.get(0).getRealName(), equalTo("TestPlayer"));
        assertThat(saved.get(0).getPassword(), equalToHash("plain-text-password"));
        assertThat(saved.get(1).getNickname(), equalTo("secondplayer"));
        assertThat(saved.get(1).getPassword(), equalToHash("other-password"));
    }

    @Test
    void shouldInformSenderAboutMissingFile() {
        given(settings.getProperty(ConverterSettings.CRAZYLOGIN_FILE_NAME)).willReturn("accounts.db");
        CommandSender sender = mock(CommandSender.class);

        converter.execute(sender);

        verify(sender).sendMessage(argThat(containsString("not found")));
        verifyNoInteractions(dataSource);
    }
}
