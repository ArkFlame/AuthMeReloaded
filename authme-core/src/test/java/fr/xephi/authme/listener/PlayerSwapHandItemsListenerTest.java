package fr.xephi.authme.listener;

import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static fr.xephi.authme.listener.EventCancelVerifier.withServiceMock;

@ExtendWith(MockitoExtension.class)
class PlayerSwapHandItemsListenerTest {

    @InjectMocks
    private PlayerSwapHandItemsListener listener;

    @Mock
    private ListenerService listenerService;

    @Test
    void shouldCancelOnlyForUnauthenticatedPlayer() {
        withServiceMock(listenerService)
            .check(listener::onPlayerSwapHandItems, PlayerSwapHandItemsEvent.class);
    }
}
