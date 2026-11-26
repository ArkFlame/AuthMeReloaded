package fr.xephi.authme.data.limbo;

import org.bukkit.entity.Player;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static fr.xephi.authme.data.limbo.AllowFlightRestoreType.DISABLE;
import static fr.xephi.authme.data.limbo.AllowFlightRestoreType.ENABLE;
import static fr.xephi.authme.data.limbo.AllowFlightRestoreType.NOTHING;
import static fr.xephi.authme.data.limbo.AllowFlightRestoreType.RESTORE;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Test for {@link AllowFlightRestoreType}.
 */
class AllowFlightRestoreTypeTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("restoreTypes")
    void shouldRestoreAllowFlight(AllowFlightRestoreType type, boolean expectedWithFly,
                                  boolean expectedWithoutFly, boolean interactsWithPlayer) {
        // given
        LimboPlayer limboWithFly = newLimboWithAllowFlight(true);
        LimboPlayer limboWithoutFly = newLimboWithAllowFlight(false);
        Player playerWithFly = mock(Player.class);
        Player playerWithoutFly = mock(Player.class);

        // when
        type.restoreAllowFlight(playerWithFly, limboWithFly);
        type.restoreAllowFlight(playerWithoutFly, limboWithoutFly);

        // then
        if (interactsWithPlayer) {
            verify(playerWithFly).setAllowFlight(expectedWithFly);
            verify(playerWithoutFly).setAllowFlight(expectedWithoutFly);
        } else {
            verifyNoInteractions(playerWithFly, playerWithoutFly);
        }
    }

    private static Stream<Arguments> restoreTypes() {
        return Stream.of(
            Arguments.of(RESTORE, true, false, true),
            Arguments.of(ENABLE, true, true, true),
            Arguments.of(DISABLE, false, false, true),
            Arguments.of(NOTHING, false, false, false));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(AllowFlightRestoreType.class)
    void shouldRemoveFlightExceptForNothingType(AllowFlightRestoreType type) {
        // given
        Player player = mock(Player.class);

        // when
        type.processPlayer(player);

        // then
        if (type == NOTHING) {
            verifyNoInteractions(player);
        } else {
            verify(player).setAllowFlight(false);
        }
    }

    private static LimboPlayer newLimboWithAllowFlight(boolean allowFlight) {
        LimboPlayer limbo = mock(LimboPlayer.class);
        given(limbo.isCanFly()).willReturn(allowFlight);
        return limbo;
    }
}
