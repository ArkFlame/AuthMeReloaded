package fr.xephi.authme.data.limbo;

import org.bukkit.entity.Player;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static fr.xephi.authme.data.limbo.LimboPlayer.DEFAULT_FLY_SPEED;
import static fr.xephi.authme.data.limbo.LimboPlayer.DEFAULT_WALK_SPEED;
import static fr.xephi.authme.data.limbo.WalkFlySpeedRestoreType.DEFAULT;
import static fr.xephi.authme.data.limbo.WalkFlySpeedRestoreType.MAX_RESTORE;
import static fr.xephi.authme.data.limbo.WalkFlySpeedRestoreType.RESTORE;
import static fr.xephi.authme.data.limbo.WalkFlySpeedRestoreType.RESTORE_NO_ZERO;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Test for {@link WalkFlySpeedRestoreType}.
 */
class WalkFlySpeedRestoreTypeTest {

    @ParameterizedTest
    @MethodSource("buildParams")
    void shouldRestoreToExpectedValue(WalkFlySpeedRestoreType type, float givenLimboWalkSpeed,
                                      float givenLimboFlySpeed, float givenPlayerWalkSpeed, float givenPlayerFlySpeed,
                                      float expectedWalkSpeed, float expectedFlySpeed) {
        // given
        LimboPlayer limbo = mock(LimboPlayer.class);
        given(limbo.getWalkSpeed()).willReturn(givenLimboWalkSpeed);
        given(limbo.getFlySpeed()).willReturn(givenLimboFlySpeed);

        Player player = mock(Player.class);
        given(player.getWalkSpeed()).willReturn(givenPlayerWalkSpeed);
        given(player.getFlySpeed()).willReturn(givenPlayerFlySpeed);

        // when
        type.restoreWalkSpeed(player, limbo);
        type.restoreFlySpeed(player, limbo);

        // then
        verify(player).setWalkSpeed(expectedWalkSpeed);
        verify(player).setFlySpeed(expectedFlySpeed);
    }

    private static Stream<Arguments> buildParams() {
        return Stream.of(
            Arguments.of(RESTORE, 0.1f, 0.4f, 0.3f, 0.9f, 0.1f, 0.4f),
            Arguments.of(RESTORE, 0.9f, 0.2f, 0.3f, 0.0f, 0.9f, 0.2f),
            Arguments.of(MAX_RESTORE, 0.3f, 0.8f, 0.5f, 0.2f, 0.5f, 0.8f),
            Arguments.of(MAX_RESTORE, 0.4f, 0.2f, 0.1f, 0.4f, 0.4f, 0.4f),
            Arguments.of(RESTORE_NO_ZERO, 0.1f, 0.2f, 0.5f, 0.1f, 0.1f, 0.2f),
            Arguments.of(RESTORE_NO_ZERO, 0.0f, 0.005f, 0.4f, 0.8f, DEFAULT_WALK_SPEED, DEFAULT_FLY_SPEED),
            Arguments.of(DEFAULT, 0.1f, 0.7f, 0.4f, 0.0f, DEFAULT_WALK_SPEED, DEFAULT_FLY_SPEED));
    }
}
