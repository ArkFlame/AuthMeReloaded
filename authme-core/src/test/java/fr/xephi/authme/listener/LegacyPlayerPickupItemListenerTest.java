package fr.xephi.authme.listener;

import org.bukkit.event.player.PlayerPickupItemEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static fr.xephi.authme.listener.EventCancelVerifier.withServiceMock;

@ExtendWith(MockitoExtension.class)
class LegacyPlayerPickupItemListenerTest {

    @InjectMocks
    private LegacyPlayerPickupItemListener listener;

    @Mock
    private ListenerService listenerService;

    @Test
    void shouldCancelOnlyForUnauthenticatedPlayer() {
        withServiceMock(listenerService)
            .check(listener::onLegacyPlayerPickupItem, PlayerPickupItemEvent.class);
    }
}
