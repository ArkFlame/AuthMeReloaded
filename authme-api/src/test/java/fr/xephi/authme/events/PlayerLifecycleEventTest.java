package fr.xephi.authme.events;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.function.Function;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.Mockito.mock;

/** Focused checks for the events that simply carry the affected player. */
class PlayerLifecycleEventTest {

    private final Player player = mock(Player.class);

    @ParameterizedTest(name = "{0}")
    @MethodSource("simplePlayerEvents")
    void playerEventExposesPlayerAndHandlerList(String name, Function<Player, CustomEvent> factory,
                                                Function<CustomEvent, Player> playerGetter,
                                                HandlerList handlerList) {
        CustomEvent event = factory.apply(player);
        assertThat(name, playerGetter.apply(event), sameInstance(player));
        assertThat(name, event.getHandlers(), sameInstance(handlerList));
        assertThat(name, event.isAsynchronous(), equalTo(false));
    }

    private static Stream<Arguments> simplePlayerEvents() {
        return Stream.of(
            Arguments.of("login", (Function<Player, CustomEvent>) p -> new LoginEvent(p),
                (Function<CustomEvent, Player>) e -> ((LoginEvent) e).getPlayer(), LoginEvent.getHandlerList()),
            Arguments.of("logout", (Function<Player, CustomEvent>) p -> new LogoutEvent(p),
                (Function<CustomEvent, Player>) e -> ((LogoutEvent) e).getPlayer(), LogoutEvent.getHandlerList()),
            Arguments.of("register", (Function<Player, CustomEvent>) p -> new RegisterEvent(p),
                (Function<CustomEvent, Player>) e -> ((RegisterEvent) e).getPlayer(), RegisterEvent.getHandlerList()));
    }

    @Test
    void loginEventRetainsCompatibilityFlag() {
        assertThat(new LoginEvent(player).isLogin(), equalTo(true));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void failedLoginEventHonorsAsyncFlag(boolean async) {
        FailedLoginEvent event = new FailedLoginEvent(player, async);
        assertThat(event.getPlayer(), sameInstance(player));
        assertThat(event.getHandlers(), sameInstance(FailedLoginEvent.getHandlerList()));
        assertThat(event.isAsynchronous(), equalTo(async));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void restoreSessionEventHonorsAsyncFlagAndCancellation(boolean async) {
        RestoreSessionEvent event = new RestoreSessionEvent(player, async);
        assertThat(event.getPlayer(), sameInstance(player));
        assertThat(event.isAsynchronous(), equalTo(async));
        assertThat(event.getHandlers(), sameInstance(RestoreSessionEvent.getHandlerList()));
        assertThat(event.isCancelled(), equalTo(false));
        event.setCancelled(true);
        assertThat(event.isCancelled(), equalTo(true));
    }
}
