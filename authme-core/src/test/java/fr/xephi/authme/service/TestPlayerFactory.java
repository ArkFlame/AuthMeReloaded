package fr.xephi.authme.service;

import fr.xephi.authme.TestHelper;
import org.bukkit.entity.Player;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * Shared fixtures for mocked players used by service tests.
 */
public final class TestPlayerFactory {

    private TestPlayerFactory() {
    }

    public static Player mockPlayerWithNameAndIp(String name, String ip) {
        Player player = mock(Player.class);
        given(player.getName()).willReturn(name);
        TestHelper.mockIpAddressToPlayer(player, ip);
        return player;
    }
}
