package fr.xephi.authme.listener;

import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.service.CommonService;
import fr.xephi.authme.settings.properties.SecuritySettings;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;

import static fr.xephi.authme.listener.TestPlayerUtils.mockPlayerWithName;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Test for {@link DoubleLoginFixListener}.
 */
@ExtendWith(MockitoExtension.class)
class DoubleLoginFixListenerTest {

    @InjectMocks
    private DoubleLoginFixListener listener;

    @Mock
    private CommonService commonService;

    @Test
    void shouldNotKickAnyoneWhenFeatureIsDisabled() {
        given(commonService.getProperty(SecuritySettings.ANTI_GHOST_PLAYERS)).willReturn(false);
        Player player = mock(Player.class);

        withBukkitServer(mock(Server.class), () -> listener.onDuplicateNameJoin(mock(PlayerJoinEvent.class)));

        verify(commonService).getProperty(SecuritySettings.ANTI_GHOST_PLAYERS);
        verifyNoInteractions(player);
    }

    @Test
    void shouldKickDuplicateNameIgnoringCase() {
        given(commonService.getProperty(SecuritySettings.ANTI_GHOST_PLAYERS)).willReturn(true);
        Player first = mockPlayerWithName("Ghost");
        Player duplicate = mockPlayerWithName("ghost");
        given(commonService.retrieveSingleMessage(duplicate, MessageKey.DOUBLE_LOGIN_FIX))
            .willReturn("Doubled login");

        withBukkitServer(serverWithPlayers(first, duplicate),
            () -> listener.onDuplicateNameJoin(mock(PlayerJoinEvent.class)));

        verify(first, never()).kickPlayer("Doubled login");
        verify(duplicate).kickPlayer("Doubled login");
    }

    @Test
    void shouldNotKickPlayersWithUniqueNames() {
        given(commonService.getProperty(SecuritySettings.ANTI_GHOST_PLAYERS)).willReturn(true);
        Player first = mockPlayerWithName("Alice");
        Player second = mockPlayerWithName("Bob");

        withBukkitServer(serverWithPlayers(first, second),
            () -> listener.onDuplicateNameJoin(mock(PlayerJoinEvent.class)));

        verify(first, never()).kickPlayer(anyString());
        verify(second, never()).kickPlayer(anyString());
        verify(commonService, never()).retrieveSingleMessage(any(), any(MessageKey.class));
    }

    private void withBukkitServer(Server server, Runnable action) {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getServer).thenReturn(server);
            action.run();
        }
    }

    private static Server serverWithPlayers(Player... players) {
        Server server = mock(Server.class);
        doReturn(Arrays.asList(players)).when(server).getOnlinePlayers();
        return server;
    }
}
