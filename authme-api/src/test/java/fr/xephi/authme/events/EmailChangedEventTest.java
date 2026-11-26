package fr.xephi.authme.events;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.Mockito.mock;

/** Focused checks for the email-change event's values and nullability contract. */
class EmailChangedEventTest {

    private final Player player = mock(Player.class);

    @Test
    void exposesPlayerAndEmailValues() {
        assertEvent("old@example.com", "new@example.com", true);
    }

    @Test
    void allowsNullOldEmailWhenAddingAddress() {
        assertEvent(null, "new@example.com", false);
    }

    private void assertEvent(String oldEmail, String newEmail, boolean asynchronous) {
        EmailChangedEvent event = new EmailChangedEvent(player, oldEmail, newEmail, asynchronous);

        assertThat(event.getPlayer(), sameInstance(player));
        assertThat(event.getOldEmail(), equalTo(oldEmail));
        assertThat(event.getNewEmail(), equalTo(newEmail));
        assertThat(event.isAsynchronous(), equalTo(asynchronous));
    }
}
