package fr.xephi.authme.util;

import fr.xephi.authme.TestHelper;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.mock;

/**
 * Test for loopback address detection in {@link PlayerUtils}.
 */
class PlayerUtilsLoopbackIpTest {

    @BeforeAll
    static void setUpLogger() {
        TestHelper.setupLogger();
    }

    @Test
    void shouldDetectIpv4LoopbackAddress() {
        // given
        Player player = mock(Player.class);
        TestHelper.mockIpAddressToPlayer(player, "127.0.0.1");

        // when
        boolean result = PlayerUtils.isPlayerIpLoopback(player);

        // then
        assertThat(result, equalTo(true));
    }

    @Test
    void shouldDetectIpv6LoopbackAddress() {
        // given
        Player player = mock(Player.class);
        TestHelper.mockIpAddressToPlayer(player, "::1");

        // when
        boolean result = PlayerUtils.isPlayerIpLoopback(player);

        // then
        assertThat(result, equalTo(true));
    }

    @Test
    void shouldNotDetectNormalAddressAsLoopback() {
        // given
        Player player = mock(Player.class);
        TestHelper.mockIpAddressToPlayer(player, "124.86.248.62");

        // when
        boolean result = PlayerUtils.isPlayerIpLoopback(player);

        // then
        assertThat(result, equalTo(false));
    }
}
