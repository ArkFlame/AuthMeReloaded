package fr.xephi.authme.process.logout;

import fr.xephi.authme.data.VerificationCodeManager;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.data.auth.PlayerCache;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.process.SyncProcessManager;
import fr.xephi.authme.service.CommonService;
import fr.xephi.authme.service.SessionService;
import fr.xephi.authme.service.bungeecord.BungeeSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Test for {@link AsynchronousLogout}.
 */
@ExtendWith(MockitoExtension.class)
class AsynchronousLogoutTest {

    @InjectMocks
    private AsynchronousLogout asynchronousLogout;

    @Mock
    private DataSource database;
    @Mock
    private CommonService service;
    @Mock
    private PlayerCache playerCache;
    @Mock
    private VerificationCodeManager codeManager;
    @Mock
    private SyncProcessManager syncProcessManager;
    @Mock
    private SessionService sessionService;
    @Mock
    private BungeeSender bungeeSender;

    @Test
    void shouldUnauthenticatePlayerEvenIfSessionUpdateFails() {
        Player player = mock(Player.class);
        given(player.getName()).willReturn("Bobby");
        PlayerAuth auth = mock(PlayerAuth.class);
        given(playerCache.getAuth("bobby")).willReturn(auth);
        doThrow(IllegalStateException.class).when(database).updateSession(auth);

        assertThrows(IllegalStateException.class, () -> asynchronousLogout.logout(player, null));

        verify(playerCache).removePlayer("bobby");
        verify(codeManager).unverify("bobby");
        verify(database).setUnlogged("bobby");
        verify(sessionService).revokeSession("bobby");
        verify(syncProcessManager).processSyncPlayerLogout(player);
    }
}
