package fr.xephi.authme.process;

import fr.xephi.authme.TestHelper;
import fr.xephi.authme.data.ProxySessionManager;
import fr.xephi.authme.service.BukkitService;
import fr.xephi.authme.service.PreJoinDialogService;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test for {@link Management}.
 */
@ExtendWith(MockitoExtension.class)
class ManagementTest {

    @InjectMocks
    private Management management;

    @Mock
    private BukkitService bukkitService;
    @Mock
    private ProxySessionManager proxySessionManager;
    @Mock
    private PreJoinDialogService preJoinDialogService;

    @Test
    void shouldQueueLogoutWhenLocationCaptureThrows() {
        TestHelper.setupLogger();
        Player player = mock(Player.class);
        when(player.getLocation()).thenThrow(new IllegalStateException("Player is not in a world"));

        management.performLogout(player);

        verify(bukkitService).runTaskOptionallyAsync(any(Runnable.class));
    }

    @ParameterizedTest(name = "{0}, online={1}, approved={2}")
    @CsvSource({"Connor, true, true", "ConNor, false, true", "OfflinePlayer, false, false"})
    void shouldForceLoginFromProxyByName(String playerName, boolean online, boolean approved) {
        if (online) {
            Player player = mock(Player.class);
            when(bukkitService.getPlayerExact(playerName)).thenReturn(player);
            when(player.isOnline()).thenReturn(true);
        } else {
            when(bukkitService.getPlayerExact(playerName)).thenReturn(null);
            when(preJoinDialogService.approvePreJoinForceLogin(playerName.toLowerCase())).thenReturn(approved);
        }

        management.forceLoginFromProxy(playerName);

        verify(proxySessionManager).processProxySessionMessage(playerName);
        if (online) {
            verify(bukkitService).runTaskOptionallyAsync(any(Runnable.class));
        } else {
            verify(preJoinDialogService).approvePreJoinForceLogin(playerName.toLowerCase());
            verify(bukkitService, never()).runTaskOptionallyAsync(any());
        }
    }
}