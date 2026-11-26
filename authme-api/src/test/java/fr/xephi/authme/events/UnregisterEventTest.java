package fr.xephi.authme.events;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.Mockito.mock;

/** Focused checks for the unregister event family, including its nullable fields. */
class UnregisterEventTest {

    private final Player player = mock(Player.class);
    private final CommandSender initiator = mock(CommandSender.class);

    @Test
    void byPlayerExposesPlayerAndAsyncFlag() {
        UnregisterByPlayerEvent sync = new UnregisterByPlayerEvent(player, false);
        assertThat(sync.getPlayer(), sameInstance(player));
        assertThat(sync.isAsynchronous(), equalTo(false));
        assertThat(sync.getHandlers(), sameInstance(UnregisterByPlayerEvent.getHandlerList()));

        UnregisterByPlayerEvent async = new UnregisterByPlayerEvent(player, true);
        assertThat(async.getPlayer(), sameInstance(player));
        assertThat(async.isAsynchronous(), equalTo(true));
    }

    @Test
    void byAdminExposesNameAndInitiator() {
        UnregisterByAdminEvent event = new UnregisterByAdminEvent(player, "Bobby", true, initiator);

        assertThat(event.getPlayer(), sameInstance(player));
        assertThat(event.getPlayerName(), equalTo("Bobby"));
        assertThat(event.getInitiator(), sameInstance(initiator));
        assertThat(event.isAsynchronous(), equalTo(true));
        assertThat(event.getHandlers(), sameInstance(UnregisterByAdminEvent.getHandlerList()));
    }

    @Test
    void byAdminAllowsNullPlayerAndInitiator() {
        UnregisterByAdminEvent event = new UnregisterByAdminEvent(null, "Bobby", false, null);

        assertThat(event.getPlayer(), nullValue());
        assertThat(event.getInitiator(), nullValue());
        assertThat(event.getPlayerName(), equalTo("Bobby"));
        assertThat(event.isAsynchronous(), equalTo(false));
    }
}
