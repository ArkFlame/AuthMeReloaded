package fr.xephi.authme.util;

import fr.xephi.authme.TestHelper;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * Test for {@link PlayerUtils}.
 */
class PlayerUtilsTest {

    @BeforeAll
    static void setUpLogger() {
        TestHelper.setupLogger();
    }

    @Test
    void shouldGetPlayerIp() {
        Player player = mock(Player.class);
        String ip = "124.86.248.62";
        TestHelper.mockIpAddressToPlayer(player, ip);

        String result = PlayerUtils.getPlayerIp(player);

        assertThat(result, equalTo(ip));
    }

    @ParameterizedTest
    @CsvSource({"false", "true"})
    void shouldCheckIfIsNpc(boolean isNpc) {
        Player player = mock(Player.class);
        given(player.hasMetadata("NPC")).willReturn(isNpc);

        assertThat(PlayerUtils.isNpc(player), equalTo(isNpc));
    }
}
