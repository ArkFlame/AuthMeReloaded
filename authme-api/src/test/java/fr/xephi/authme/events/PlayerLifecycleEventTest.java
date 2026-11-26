package fr.xephi.authme.events;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.Mockito.mock;

/** Focused checks for the events that simply carry the affected player. */
class PlayerLifecycleEventTest {

    private final Player player = mock(Player.class);

    @Test
    void loginEventExposesPlayerAndCompatibilityFlag() {
        LoginEvent event = new LoginEvent(player);

        assertThat(event.getPlayer(), sameInstance(player));
        assertThat(event.getHandlers(), sameInstance(LoginEvent.getHandlerList()));
        assertThat(event.isAsynchronous(), equalTo(false));
        assertThat(event.isLogin(), equalTo(true));
    }

    @Test
    void logoutEventExposesPlayer() {
        LogoutEvent event = new LogoutEvent(player);

        assertThat(event.getPlayer(), sameInstance(player));
        assertThat(event.getHandlers(), sameInstance(LogoutEvent.getHandlerList()));
        assertThat(event.isAsynchronous(), equalTo(false));
    }

    @Test
    void registerEventExposesPlayer() {
        RegisterEvent event = new RegisterEvent(player);

        assertThat(event.getPlayer(), sameInstance(player));
        assertThat(event.getHandlers(), sameInstance(RegisterEvent.getHandlerList()));
        assertThat(event.isAsynchronous(), equalTo(false));
    }

    @Test
    void failedLoginEventHonorsAsyncFlag() {
        FailedLoginEvent sync = new FailedLoginEvent(player, false);
        assertThat(sync.getPlayer(), sameInstance(player));
        assertThat(sync.getHandlers(), sameInstance(FailedLoginEvent.getHandlerList()));
        assertThat(sync.isAsynchronous(), equalTo(false));

        FailedLoginEvent async = new FailedLoginEvent(player, true);
        assertThat(async.getPlayer(), sameInstance(player));
        assertThat(async.isAsynchronous(), equalTo(true));
    }

    @Test
    void restoreSessionEventHonorsAsyncFlagAndCancellation() {
        RestoreSessionEvent sync = new RestoreSessionEvent(player, false);
        assertThat(sync.getPlayer(), sameInstance(player));
        assertThat(sync.isAsynchronous(), equalTo(false));
        assertThat(sync.getHandlers(), sameInstance(RestoreSessionEvent.getHandlerList()));

        RestoreSessionEvent async = new RestoreSessionEvent(player, true);
        assertThat(async.getPlayer(), sameInstance(player));
        assertThat(async.isAsynchronous(), equalTo(true));
        assertThat(async.isCancelled(), equalTo(false));
        async.setCancelled(true);
        assertThat(async.isCancelled(), equalTo(true));
    }
}
