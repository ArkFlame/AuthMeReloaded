package fr.xephi.authme.process;

import fr.xephi.authme.data.ProxySessionManager;
import fr.xephi.authme.process.login.AsynchronousLogin;
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
    private AsynchronousLogin asynchronousLogin;
    @Mock
    private ProxySessionManager proxySessionManager;
    @Mock
    private PreJoinDialogService preJoinDialogService;

    @Test
    void shouldForceLoginFromProxyByNameQueuesBeforeOnlineForceLogin() {
        String playerName = "Connor";
        Player player = mock(Player.class);
        when(bukkitService.getPlayerExact(playerName)).thenReturn(player);
        when(player.isOnline()).thenReturn(true);

        management.forceLoginFromProxy(playerName);

        verify(proxySessionManager).processProxySessionMessage(playerName);
        verify(bukkitService).runTaskOptionallyAsync(any(Runnable.class));
    }

    @ParameterizedTest(name = "{0}, approved={1}")
    @CsvSource({"ConNor, true", "OfflinePlayer, false"})
    void shouldForceLoginFromProxyByNameForOfflinePlayer(String playerName, boolean approved) {
        when(bukkitService.getPlayerExact(playerName)).thenReturn(null);
        when(preJoinDialogService.approvePreJoinForceLogin(playerName.toLowerCase())).thenReturn(approved);

        management.forceLoginFromProxy(playerName);

        verify(proxySessionManager).processProxySessionMessage(playerName);
        verify(preJoinDialogService).approvePreJoinForceLogin(playerName.toLowerCase());
        verify(bukkitService, never()).runTaskOptionallyAsync(any());
    }
}