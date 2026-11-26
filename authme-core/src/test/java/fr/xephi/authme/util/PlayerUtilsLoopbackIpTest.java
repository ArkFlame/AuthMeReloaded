package fr.xephi.authme.util;

import fr.xephi.authme.TestHelper;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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

    @ParameterizedTest
    @CsvSource({
        "127.0.0.1,     true",
        "::1,           true",
        "124.86.248.62, false"
    })
    void shouldDetectLoopbackAddresses(String ip, boolean expected) {
        Player player = mock(Player.class);
        TestHelper.mockIpAddressToPlayer(player, ip);

        boolean result = PlayerUtils.isPlayerIpLoopback(player);

        assertThat(result, equalTo(expected));
    }
}
