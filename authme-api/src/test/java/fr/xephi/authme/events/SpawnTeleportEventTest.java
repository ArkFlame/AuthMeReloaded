package fr.xephi.authme.events;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/** Focused checks for the spawn teleport variants and their inherited teleport state. */
class SpawnTeleportEventTest {

    private final Player player = mock(Player.class);
    private final Location from = mock(Location.class);
    private final Location to = mock(Location.class);

    @Test
    void firstSpawnEventExposesTeleportState() {
        given(player.getLocation()).willReturn(from);

        FirstSpawnTeleportEvent event = new FirstSpawnTeleportEvent(player, to);

        assertThat(event.getPlayer(), sameInstance(player));
        assertThat(event.getFrom(), sameInstance(from));
        assertThat(event.getTo(), sameInstance(to));
        assertThat(event.isAsynchronous(), equalTo(false));
        assertThat(event.getHandlers(), sameInstance(FirstSpawnTeleportEvent.getHandlerList()));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void spawnEventExposesAuthenticationFlag(boolean authenticated) {
        given(player.getLocation()).willReturn(from);

        SpawnTeleportEvent event = new SpawnTeleportEvent(player, to, authenticated);
        assertThat(event.isAuthenticated(), equalTo(authenticated));
        assertThat(event.getPlayer(), sameInstance(player));
        assertThat(event.getFrom(), sameInstance(from));
        assertThat(event.getTo(), sameInstance(to));
        assertThat(event.isAsynchronous(), equalTo(false));
        assertThat(event.getHandlers(), sameInstance(SpawnTeleportEvent.getHandlerList()));
    }

    @Test
    void destinationRemainsMutable() {
        given(player.getLocation()).willReturn(from);

        SpawnTeleportEvent event = new SpawnTeleportEvent(player, to, false);
        Location replacement = mock(Location.class);
        event.setTo(replacement);
        assertThat(event.getTo(), sameInstance(replacement));
        assertThat(event.getFrom(), sameInstance(from));
    }
}
