package fr.xephi.authme.events;

import org.bukkit.entity.Player;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.Mockito.mock;

/** Verifies the veto flags exposed by the asynchronous login and register events. */
class AsyncEventContractTest {

    private final Player player = mock(Player.class);

    @ParameterizedTest(name = "{0}")
    @MethodSource("vetoEvents")
    void defaultsToAllowedAndHonorsVeto(String name, BiFunction<Player, Boolean, CustomEvent> factory,
                                        Function<CustomEvent, Player> playerGetter,
                                        Function<CustomEvent, Boolean> isAllowed,
                                        BiConsumer<CustomEvent, Boolean> setAllowed, boolean async) {
        CustomEvent event = factory.apply(player, async);

        assertThat(name, playerGetter.apply(event), sameInstance(player));
        assertThat(name, isAllowed.apply(event), equalTo(true));
        assertThat(name, event.isAsynchronous(), equalTo(async));

        setAllowed.accept(event, false);
        assertThat(name, isAllowed.apply(event), equalTo(false));

        setAllowed.accept(event, true);
        assertThat(name, isAllowed.apply(event), equalTo(true));
    }

    private static Stream<Arguments> vetoEvents() {
        return Stream.of(
            Arguments.of("preLogin",
                (BiFunction<Player, Boolean, CustomEvent>) (p, flag) -> new AuthMeAsyncPreLoginEvent(p, flag),
                (Function<CustomEvent, Player>) e -> ((AuthMeAsyncPreLoginEvent) e).getPlayer(),
                (Function<CustomEvent, Boolean>) e -> ((AuthMeAsyncPreLoginEvent) e).canLogin(),
                (BiConsumer<CustomEvent, Boolean>) (e, value) -> ((AuthMeAsyncPreLoginEvent) e).setCanLogin(value),
                true),
            Arguments.of("preRegister",
                (BiFunction<Player, Boolean, CustomEvent>) (p, flag) -> new AuthMeAsyncPreRegisterEvent(p, flag),
                (Function<CustomEvent, Player>) e -> ((AuthMeAsyncPreRegisterEvent) e).getPlayer(),
                (Function<CustomEvent, Boolean>) e -> ((AuthMeAsyncPreRegisterEvent) e).canRegister(),
                (BiConsumer<CustomEvent, Boolean>) (e, value) -> ((AuthMeAsyncPreRegisterEvent) e).setCanRegister(value),
                false));
    }
}
