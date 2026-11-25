package fr.xephi.authme.listener;

import org.bukkit.event.player.PlayerPickupArrowEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PlayerPickupArrowListenerTest {

    @InjectMocks
    private PlayerPickupArrowListener listener;

    @Mock
    private ListenerService listenerService;

    @Test
    void shouldCancelPickupOfUnauthenticatedPlayer() {
        PlayerPickupArrowEvent event = mock(PlayerPickupArrowEvent.class);
        given(listenerService.shouldCancelEvent(event)).willReturn(true);

        listener.onPlayerPickupArrow(event);

        verify(event).setCancelled(true);
    }

    @Test
    void shouldNotCancelPickupOfAuthenticatedPlayer() {
        PlayerPickupArrowEvent event = mock(PlayerPickupArrowEvent.class);
        given(listenerService.shouldCancelEvent(event)).willReturn(false);

        listener.onPlayerPickupArrow(event);

        verify(event, never()).setCancelled(true);
    }
}
