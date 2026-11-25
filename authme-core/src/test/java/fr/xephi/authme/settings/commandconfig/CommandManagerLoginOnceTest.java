package fr.xephi.authme.settings.commandconfig;

import com.google.common.io.Files;
import fr.xephi.authme.TestHelper;
import fr.xephi.authme.service.BukkitService;
import fr.xephi.authme.service.GeoIpService;
import fr.xephi.authme.settings.SettingsMigrationService;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

import static fr.xephi.authme.service.BukkitServiceTestHelper.setBukkitServiceToScheduleSyncDelayedTaskWithEntityAndDelay;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Verifies that login commands are only run once per session, even if a player is sent back
 * to the auth server by a proxy (#3095).
 */
@ExtendWith(MockitoExtension.class)
class CommandManagerLoginOnceTest {

    private Player player;
    private CommandManager manager;

    @InjectMocks
    private CommandMigrationService commandMigrationService;

    @Mock
    private BukkitService bukkitService;
    @Mock
    private GeoIpService geoIpService;
    @Mock
    private SettingsMigrationService settingsMigrationService;

    @TempDir
    File temporaryFolder;

    @BeforeEach
    void setup() {
        player = mock(Player.class);
        given(player.getName()).willReturn("Bobby");
        TestHelper.mockIpAddressToPlayer(player, "127.0.0.3");
        copyCommandsYml();
        manager = new CommandManager(temporaryFolder, bukkitService, geoIpService, commandMigrationService);
        setBukkitServiceToScheduleSyncDelayedTaskWithEntityAndDelay(bukkitService);
    }

    @Test
    void shouldRunLoginCommandsOnlyOnceForSameSession() {
        // when
        manager.runCommandsOnLogin(player, Collections.emptyList());
        verify(bukkitService).dispatchConsoleCommand("msg Bobby Welcome back");
        verify(bukkitService).dispatchCommand(any(Player.class), eq("motd"));
        verify(bukkitService).dispatchCommand(any(Player.class), eq("list"));
        verify(bukkitService).scheduleSyncDelayedTask(eq(player), any(Runnable.class), eq(60L));
        verify(bukkitService).scheduleSyncDelayedTask(eq(player), any(Runnable.class), eq(120L));
        verifyNoMoreInteractions(bukkitService);

        // a proxy sends the player back to the auth server without a new login
        manager.runCommandsOnLogin(player, Collections.emptyList());

        // then
        verifyNoMoreInteractions(bukkitService);
    }

    @Test
    void shouldIgnoreNameCaseWhenCheckingForLoginCommands() {
        // given
        Player samePlayerWithDifferentCase = mock(Player.class);
        given(samePlayerWithDifferentCase.getName()).willReturn("BOBBY");

        // when
        manager.runCommandsOnLogin(player, Collections.emptyList());
        manager.runCommandsOnLogin(samePlayerWithDifferentCase, Collections.emptyList());

        // then
        verify(bukkitService).dispatchConsoleCommand("msg Bobby Welcome back");
        verify(bukkitService, never()).dispatchConsoleCommand("msg BOBBY Welcome back");
    }

    @Test
    void shouldRunLoginCommandsAgainAfterLogout() {
        // when
        manager.runCommandsOnLogin(player, Collections.emptyList());
        manager.runCommandsOnLogin(player, Collections.emptyList());
        manager.runCommandsOnLogout(player);
        manager.runCommandsOnLogin(player, Collections.emptyList());

        // then
        verify(bukkitService, times(2)).dispatchConsoleCommand("msg Bobby Welcome back");
        verify(bukkitService, times(2)).dispatchCommand(any(Player.class), eq("motd"));
        verify(bukkitService).dispatchConsoleCommand("broadcast Bobby (127.0.0.3) logged out");
    }

    @Test
    void shouldRunLoginCommandsAgainAfterUnregister() {
        // when
        manager.runCommandsOnLogin(player, Collections.emptyList());
        manager.runCommandsOnLogin(player, Collections.emptyList());
        manager.runCommandsOnUnregister(player);
        manager.runCommandsOnLogin(player, Collections.emptyList());

        // then
        verify(bukkitService, times(2)).dispatchConsoleCommand("msg Bobby Welcome back");
        verify(bukkitService, times(2)).dispatchCommand(any(Player.class), eq("motd"));
    }

    private void copyCommandsYml() {
        File source = TestHelper.getJarFile("/fr/xephi/authme/settings/commandconfig/commands.complete.yml");
        try {
            Files.copy(source, new File(temporaryFolder, "commands.yml"));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
