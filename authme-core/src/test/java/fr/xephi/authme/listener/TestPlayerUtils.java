package fr.xephi.authme.listener;

import org.bukkit.entity.Player;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * Shared helpers for setting up player mocks and addresses in command and listener tests.
 */
public final class TestPlayerUtils {

    private TestPlayerUtils() {
    }

    public static Player mockPlayerWithName(String name) {
        Player player = mock(Player.class);
        given(player.getName()).willReturn(name);
        return player;
    }

    public static InetAddress createInetAddress(String ip) {
        try {
            return InetAddress.getByName(ip);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Invalid IP address: " + ip, e);
        }
    }
}
