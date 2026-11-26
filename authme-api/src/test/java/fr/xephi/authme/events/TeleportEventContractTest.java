package fr.xephi.authme.events;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/** Verifies the origin/destination contract shared by all teleport events. */
class TeleportEventContractTest {

    private final Player player = mock(Player.class);
    private final Location from = mock(Location.class);
    private final Location to = mock(Location.class);

    @Test
    void shouldExposeImmutableOriginAndMutableDestination() {
        given(player.getLocation()).willReturn(from);
        AuthMeTeleportEvent event = new AuthMeTeleportEvent(player, to);

        assertThat(event.getPlayer(), sameInstance(player));
        assertThat(event.getFrom(), sameInstance(from));
        assertThat(event.getTo(), sameInstance(to));
        assertThat(event.isAsynchronous(), equalTo(false));

        Location replacement = mock(Location.class);
        event.setTo(replacement);
        assertThat(event.getTo(), sameInstance(replacement));
        assertThat(event.getFrom(), sameInstance(from));

        event.setTo(null);
        assertThat(event.getTo(), nullValue());
    }

    @Test
    void shouldInheritCancellationAndHandlerList() {
        given(player.getLocation()).willReturn(from);
        AuthMeTeleportEvent event = new AuthMeTeleportEvent(player, to);

        assertThat(event.isCancelled(), equalTo(false));
        event.setCancelled(true);
        assertThat(event.isCancelled(), equalTo(true));
        assertThat(event.getHandlers(), sameInstance(AuthMeTeleportEvent.getHandlerList()));
    }
}
