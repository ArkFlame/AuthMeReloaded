package fr.xephi.authme.events;

import org.bukkit.entity.Player;
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
    void playerEventExposesPlayer(String name, Function<Player, CustomEvent> factory,
                                  Function<CustomEvent, Player> playerGetter) {
        CustomEvent event = factory.apply(player);
        assertThat(name, playerGetter.apply(event), sameInstance(player));
        assertThat(name, event.isAsynchronous(), equalTo(false));
        if (event instanceof LoginEvent) {
            assertThat(((LoginEvent) event).isLogin(), equalTo(true));
        }
    }

    private static Stream<Arguments> simplePlayerEvents() {
        return Stream.of(
            Arguments.of("login", (Function<Player, CustomEvent>) p -> new LoginEvent(p),
                (Function<CustomEvent, Player>) e -> ((LoginEvent) e).getPlayer()),
            Arguments.of("logout", (Function<Player, CustomEvent>) p -> new LogoutEvent(p),
                (Function<CustomEvent, Player>) e -> ((LogoutEvent) e).getPlayer()),
            Arguments.of("register", (Function<Player, CustomEvent>) p -> new RegisterEvent(p),
                (Function<CustomEvent, Player>) e -> ((RegisterEvent) e).getPlayer()));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void eventsHonorAsyncFlag(boolean async) {
        FailedLoginEvent failedLogin = new FailedLoginEvent(player, async);
        assertThat(failedLogin.getPlayer(), sameInstance(player));
        assertThat(failedLogin.isAsynchronous(), equalTo(async));

        RestoreSessionEvent restore = new RestoreSessionEvent(player, async);
        assertThat(restore.getPlayer(), sameInstance(player));
        assertThat(restore.isAsynchronous(), equalTo(async));
        assertThat(restore.isCancelled(), equalTo(false));
        restore.setCancelled(true);
        assertThat(restore.isCancelled(), equalTo(true));
    }
}
