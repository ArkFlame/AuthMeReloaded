package fr.xephi.authme.events;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.mock;

class PlayerPreAuthEventTest {

    private final Player player = mock(Player.class);

    @Test
    void shouldExposeJoinData() {
        PlayerPreAuthEvent event = new PlayerPreAuthEvent(player, true, PlayerPreAuthEvent.Source.PROXY, false);
        assertThat(event.getPlayer(), equalTo(player));
        assertThat(event.isRegistered(), equalTo(true));
        assertThat(event.getSource(), equalTo(PlayerPreAuthEvent.Source.PROXY));
        assertThat(event.isCancelled(), equalTo(false));
        assertThat(event.isForceLoginRequested(), equalTo(false));
        assertThat(event.isAsynchronous(), equalTo(false));
    }

    @Test
    void honorsAsyncFlag() {
        PlayerPreAuthEvent event = new PlayerPreAuthEvent(player, false, PlayerPreAuthEvent.Source.JOIN, true);

        assertThat(event.getPlayer(), equalTo(player));
        assertThat(event.getSource(), equalTo(PlayerPreAuthEvent.Source.JOIN));
        assertThat(event.isAsynchronous(), equalTo(true));
    }

    @Test
    void forceLoginOnlyHonoredForRegisteredPlayers() {
        PlayerPreAuthEvent registered = new PlayerPreAuthEvent(player, true, PlayerPreAuthEvent.Source.JOIN, false);
        registered.forceLogin();
        assertThat(registered.isForceLoginRequested(), equalTo(true));

        PlayerPreAuthEvent unregistered = new PlayerPreAuthEvent(player, false, PlayerPreAuthEvent.Source.JOIN, false);
        unregistered.forceLogin();
        assertThat(unregistered.isForceLoginRequested(), equalTo(false));
    }

    @Test
    void cancellingClearsForceLoginAndViceVersa() {
        PlayerPreAuthEvent event = new PlayerPreAuthEvent(player, true, PlayerPreAuthEvent.Source.JOIN, false);
        event.forceLogin();
        event.skipAuthentication();
        assertThat(event.isCancelled(), equalTo(true));
        assertThat(event.isForceLoginRequested(), equalTo(false));

        event.forceLogin();
        assertThat(event.isCancelled(), equalTo(false));
        assertThat(event.isForceLoginRequested(), equalTo(true));
    }

    @Test
    void sourceEnumExposesJoinAndProxy() {
        assertThat(PlayerPreAuthEvent.Source.values(),
            arrayContaining(PlayerPreAuthEvent.Source.JOIN, PlayerPreAuthEvent.Source.PROXY));
    }
}
