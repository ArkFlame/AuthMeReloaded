package fr.xephi.authme.command.executable.authme;

import fr.xephi.authme.service.BukkitService;
import fr.xephi.authme.task.purge.PurgeService;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Set;

import static com.google.common.collect.Sets.newHashSet;
import static org.hamcrest.Matchers.arrayContainingInAnyOrder;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.hamcrest.MockitoHamcrest.argThat;

/**
 * Test for {@link PurgeBannedPlayersCommand}.
 */
@ExtendWith(MockitoExtension.class)
class PurgeBannedPlayersCommandTest {

    @InjectMocks
    private PurgeBannedPlayersCommand command;

    @Mock
    private PurgeService purgeService;

    @Mock
    private BukkitService bukkitService;

    @Test
    void shouldForwardRequestToService() {
        // given
        OfflinePlayer[] players = offlinePlayersWithNames(
            "bannedPlayer", "other_banned", "evilplayer", "Someone");
        given(bukkitService.getBannedPlayers()).willReturn(newHashSet(players));
        CommandSender sender = mock(CommandSender.class);

        // when
        command.executeCommand(sender, Collections.emptyList());

        // then
        verify(bukkitService).getBannedPlayers();
        verify(purgeService).purgePlayers(eq(sender),
            eq(newHashSet("bannedplayer", "other_banned", "evilplayer", "someone")),
            argThat(arrayContainingInAnyOrder(players)));
    }

    @Test
    void shouldSkipPlayersWithNullNameButStillPurgeThem() {
        // given
        OfflinePlayer namedPlayer = offlinePlayersWithNames("BannedPlayer")[0];
        OfflinePlayer unnamedPlayer = mock(OfflinePlayer.class);
        given(unnamedPlayer.getName()).willReturn(null);
        Set<OfflinePlayer> players = newHashSet(namedPlayer, unnamedPlayer);
        given(bukkitService.getBannedPlayers()).willReturn(players);
        CommandSender sender = mock(CommandSender.class);

        // when
        command.executeCommand(sender, Collections.emptyList());

        // then
        verify(purgeService).purgePlayers(eq(sender), eq(newHashSet("bannedplayer")),
            argThat(arrayContainingInAnyOrder(players.toArray(new OfflinePlayer[0]))));
    }

    private static OfflinePlayer[] offlinePlayersWithNames(String... names) {
        OfflinePlayer[] players = new OfflinePlayer[names.length];
        for (int i = 0; i < names.length; ++i) {
            OfflinePlayer player = mock(OfflinePlayer.class);
            given(player.getName()).willReturn(names[i]);
            players[i] = player;
        }
        return players;
    }
}
