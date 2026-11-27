package fr.xephi.authme.listener;

import org.bukkit.entity.Player;
import org.mockito.MockMakers;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

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

    @SuppressWarnings("unchecked")
    public static <T> T inlineSpy(T instance) {
        return mock((Class<T>) instance.getClass(), withSettings().spiedInstance(instance)
            .defaultAnswer(CALLS_REAL_METHODS).mockMaker(MockMakers.INLINE));
    }

    public static InetAddress createInetAddress(String ip) {
        try {
            return InetAddress.getByName(ip);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Invalid IP address: " + ip, e);
        }
    }
}
