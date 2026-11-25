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
import java.util.Collection;

import static org.mockito.BDDMockito.given;
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

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getServer).thenReturn(mock(Server.class));
            listener.onDuplicateNameJoin(mock(PlayerJoinEvent.class));
        }

        verify(commonService).getProperty(SecuritySettings.ANTI_GHOST_PLAYERS);
        verifyNoInteractions(player);
    }

    @Test
    void shouldKickDuplicateNameIgnoringCase() {
        given(commonService.getProperty(SecuritySettings.ANTI_GHOST_PLAYERS)).willReturn(true);
        Player first = mockPlayer("Ghost");
        Player duplicate = mockPlayer("ghost");
        given(commonService.retrieveSingleMessage(duplicate, MessageKey.DOUBLE_LOGIN_FIX))
            .willReturn("Doubled login");

        Server server = serverWithPlayers(first, duplicate);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getServer).thenReturn(server);
            listener.onDuplicateNameJoin(mock(PlayerJoinEvent.class));
        }

        verify(first, never()).kickPlayer("Doubled login");
        verify(duplicate).kickPlayer("Doubled login");
    }

    @Test
    void shouldNotKickPlayersWithUniqueNames() {
        given(commonService.getProperty(SecuritySettings.ANTI_GHOST_PLAYERS)).willReturn(true);
        Player first = mockPlayer("Alice");
        Player second = mockPlayer("Bob");

        Server server = serverWithPlayers(first, second);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getServer).thenReturn(server);
            listener.onDuplicateNameJoin(mock(PlayerJoinEvent.class));
        }

        verify(first, never()).kickPlayer(org.mockito.ArgumentMatchers.anyString());
        verify(second, never()).kickPlayer(org.mockito.ArgumentMatchers.anyString());
        verify(commonService, never()).retrieveSingleMessage(org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(MessageKey.class));
    }

    private static Player mockPlayer(String name) {
        Player player = mock(Player.class);
        given(player.getName()).willReturn(name);
        return player;
    }

    private static Server serverWithPlayers(Player... players) {
        Server server = mock(Server.class);
        org.mockito.Mockito.doReturn(Arrays.asList(players)).when(server).getOnlinePlayers();
        return server;
    }
}
