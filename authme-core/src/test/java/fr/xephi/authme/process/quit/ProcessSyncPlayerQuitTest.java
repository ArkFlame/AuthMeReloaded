package fr.xephi.authme.process.quit;

import fr.xephi.authme.data.limbo.LimboService;
import fr.xephi.authme.settings.commandconfig.CommandManager;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Test for {@link ProcessSyncPlayerQuit}.
 */
@ExtendWith(MockitoExtension.class)
class ProcessSyncPlayerQuitTest {

    @InjectMocks
    private ProcessSyncPlayerQuit processSyncPlayerQuit;

    @Mock
    private LimboService limboService;
    @Mock
    private CommandManager commandManager;

    @ParameterizedTest(name = "online={0}")
    @ValueSource(booleans = {true, false})
    void shouldHandleQuitForOnlineState(boolean isOnline) {
        Player player = mock(Player.class);
        given(player.isOnline()).willReturn(isOnline);

        processSyncPlayerQuit.processSyncQuit(player, isOnline);

        if (isOnline) {
            verify(commandManager).runCommandsOnLogout(player);
            verify(player).leaveVehicle();
            verifyNoInteractions(limboService);
        } else {
            verify(limboService).restoreData(player);
            verify(player, never()).leaveVehicle();
            verifyNoInteractions(commandManager);
        }
    }
}
