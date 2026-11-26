package fr.xephi.authme.events;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.Mockito.mock;

/** Focused checks for the unregister event family, including its nullable fields. */
class UnregisterEventTest {

    private final Player player = mock(Player.class);
    private final CommandSender initiator = mock(CommandSender.class);

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void byPlayerExposesPlayerAndAsyncFlag(boolean async) {
        UnregisterByPlayerEvent event = new UnregisterByPlayerEvent(player, async);
        assertThat(event.getPlayer(), sameInstance(player));
        assertThat(event.isAsynchronous(), equalTo(async));
    }

    @Test
    void byAdminExposesNameAndInitiator() {
        UnregisterByAdminEvent event = new UnregisterByAdminEvent(player, "Bobby", true, initiator);

        assertThat(event.getPlayer(), sameInstance(player));
        assertThat(event.getPlayerName(), equalTo("Bobby"));
        assertThat(event.getInitiator(), sameInstance(initiator));
        assertThat(event.isAsynchronous(), equalTo(true));
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
