package fr.xephi.authme.events;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.Mockito.mock;

/** Verifies the veto flags exposed by the asynchronous login and register events. */
class AsyncEventContractTest {

    private final Player player = mock(Player.class);

    @Test
    void preLoginDefaultsToAllowedAndHonorsVeto() {
        AuthMeAsyncPreLoginEvent event = new AuthMeAsyncPreLoginEvent(player, true);

        assertThat(event.getPlayer(), sameInstance(player));
        assertThat(event.canLogin(), equalTo(true));
        assertThat(event.isAsynchronous(), equalTo(true));

        event.setCanLogin(false);
        assertThat(event.canLogin(), equalTo(false));

        event.setCanLogin(true);
        assertThat(event.canLogin(), equalTo(true));
    }

    @Test
    void preRegisterDefaultsToAllowedAndHonorsVeto() {
        AuthMeAsyncPreRegisterEvent event = new AuthMeAsyncPreRegisterEvent(player, false);

        assertThat(event.getPlayer(), sameInstance(player));
        assertThat(event.canRegister(), equalTo(true));
        assertThat(event.isAsynchronous(), equalTo(false));

        event.setCanRegister(false);
        assertThat(event.canRegister(), equalTo(false));

        event.setCanRegister(true);
        assertThat(event.canRegister(), equalTo(true));
    }
}
