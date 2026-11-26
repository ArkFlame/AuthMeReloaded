package fr.xephi.authme.events;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.Mockito.mock;

/** Focused checks for the email-change event's values and cancellation contract. */
class EmailChangedEventTest {

    private final Player player = mock(Player.class);

    @Test
    void exposesPlayerAndEmailValues() {
        EmailChangedEvent event = new EmailChangedEvent(player, "old@example.com", "new@example.com", true);

        assertThat(event.getPlayer(), sameInstance(player));
        assertThat(event.getOldEmail(), equalTo("old@example.com"));
        assertThat(event.getNewEmail(), equalTo("new@example.com"));
        assertThat(event.isAsynchronous(), equalTo(true));
        assertThat(event.getHandlers(), sameInstance(EmailChangedEvent.getHandlerList()));
    }

    @Test
    void allowsNullOldEmailWhenAddingAddress() {
        EmailChangedEvent event = new EmailChangedEvent(player, null, "new@example.com", false);

        assertThat(event.getOldEmail(), nullValue());
        assertThat(event.getNewEmail(), equalTo("new@example.com"));
        assertThat(event.isAsynchronous(), equalTo(false));
    }

    @Test
    void cancellationToggles() {
        EmailChangedEvent event = new EmailChangedEvent(player, "old@example.com", "new@example.com", false);

        assertThat(event.isCancelled(), equalTo(false));
        event.setCancelled(true);
        assertThat(event.isCancelled(), equalTo(true));
        event.setCancelled(false);
        assertThat(event.isCancelled(), equalTo(false));
    }
}
