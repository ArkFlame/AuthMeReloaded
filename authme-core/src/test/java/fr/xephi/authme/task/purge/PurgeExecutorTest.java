package fr.xephi.authme.task.purge;

import fr.xephi.authme.TestHelper;
import fr.xephi.authme.permission.PermissionsManager;
import fr.xephi.authme.service.PluginHookService;
import fr.xephi.authme.settings.Settings;
import fr.xephi.authme.settings.properties.PurgeSettings;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Test for the file and permission purges of {@link PurgeExecutor}.
 */
@ExtendWith(MockitoExtension.class)
class PurgeExecutorTest {

    @InjectMocks
    private PurgeExecutor executor;

    @Mock
    private Settings settings;
    @Mock
    private PermissionsManager permissionsManager;
    @Mock
    private PluginHookService pluginHookService;
    @Mock
    private Server server;

    @TempDir
    File tempDir;

    @BeforeAll
    static void initLogger() {
        TestHelper.setupLogger();
    }

    private static OfflinePlayer playerWithId(UUID id) {
        OfflinePlayer player = mock(OfflinePlayer.class);
        given(player.getUniqueId()).willReturn(id);
        return player;
    }

    private static File touch(File folder, String name) throws IOException {
        File file = new File(folder, name);
        file.getParentFile().mkdirs();
        assertThat(file.createNewFile(), equalTo(true));
        return file;
    }

    @Test
    void shouldDeleteOnlyTheDatFilesOfPurgedPlayers() throws IOException {
        UUID purged = UUID.randomUUID();
        UUID kept = UUID.randomUUID();
        given(settings.getProperty(PurgeSettings.REMOVE_PLAYER_DAT)).willReturn(true);
        given(settings.getProperty(PurgeSettings.DEFAULT_WORLD)).willReturn("world");
        given(server.getWorldContainer()).willReturn(tempDir);
        File purgedFile = touch(tempDir, "world/players/" + purged + ".dat");
        File keptFile = touch(tempDir, "world/players/" + kept + ".dat");

        executor.purgeDat(Collections.singletonList(playerWithId(purged)));

        assertThat(purgedFile.exists(), equalTo(false));
        assertThat(keptFile.exists(), equalTo(true));
    }

    @Test
    void shouldLeaveDatFilesAloneWhenThePurgeIsDisabled() throws IOException {
        UUID id = UUID.randomUUID();
        given(settings.getProperty(PurgeSettings.REMOVE_PLAYER_DAT)).willReturn(false);
        File file = touch(tempDir, "world/players/" + id + ".dat");

        executor.purgeDat(Collections.singletonList(mock(OfflinePlayer.class)));

        assertThat(file.exists(), equalTo(true));
    }

    @Test
    void shouldDeleteOnlyTheEssentialsFilesOfPurgedPlayers() throws IOException {
        UUID purged = UUID.randomUUID();
        UUID kept = UUID.randomUUID();
        given(settings.getProperty(PurgeSettings.REMOVE_ESSENTIALS_FILES)).willReturn(true);
        given(pluginHookService.getEssentialsDataFolder()).willReturn(tempDir);
        File purgedFile = touch(tempDir, "userdata/" + purged + ".yml");
        File keptFile = touch(tempDir, "userdata/" + kept + ".yml");

        executor.purgeEssentials(Collections.singletonList(playerWithId(purged)));

        assertThat(purgedFile.exists(), equalTo(false));
        assertThat(keptFile.exists(), equalTo(true));
    }

    @Test
    void shouldSkipEssentialsWhenThePluginIsNotLoaded() {
        given(settings.getProperty(PurgeSettings.REMOVE_ESSENTIALS_FILES)).willReturn(true);
        given(pluginHookService.getEssentialsDataFolder()).willReturn(null);

        executor.purgeEssentials(Collections.singletonList(mock(OfflinePlayer.class)));

        verify(pluginHookService).getEssentialsDataFolder();
    }

    @Test
    void shouldOnlyRemoveGroupsOfPlayersWhoseDataCouldBeLoaded() {
        OfflinePlayer loaded = mock(OfflinePlayer.class);
        OfflinePlayer failed = mock(OfflinePlayer.class);
        given(settings.getProperty(PurgeSettings.REMOVE_PERMISSIONS)).willReturn(true);
        given(permissionsManager.loadUserData(loaded)).willReturn(true);
        given(permissionsManager.loadUserData(failed)).willReturn(false);

        executor.purgePermissions(Arrays.asList(loaded, failed));

        verify(permissionsManager).removeAllGroups(loaded);
        verify(permissionsManager, never()).removeAllGroups(failed);
    }
}
