package fr.xephi.authme.listener;

import org.bukkit.event.entity.EntityAirChangeEvent;
import org.bukkit.event.player.PlayerPickupArrowEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static fr.xephi.authme.listener.EventCancelVerifier.withServiceMock;

/**
 * Groups the trivial listeners whose only job is to cancel an event when the listener service
 * reports the player as unauthorized. Each listener's assertions are delegated to
 * {@link EventCancelVerifier#check(java.util.function.Consumer, Class)}.
 */
@ExtendWith(MockitoExtension.class)
class SimpleCancelListenerTest {

    @Mock
    private ListenerService listenerService;

    @InjectMocks
    private EntityAirChangeListener airChangeListener;

    @InjectMocks
    private PlayerPickupArrowListener arrowListener;

    @InjectMocks
    private PlayerSwapHandItemsListener swapListener;

    @InjectMocks
    private LegacyPlayerPickupItemListener pickupListener;

    @Test
    void shouldCancelAirChangeOnlyForUnauthenticatedPlayer() {
        withServiceMock(listenerService)
            .check(airChangeListener::onPlayerAirChange, EntityAirChangeEvent.class);
    }

    @Test
    void shouldCancelArrowPickupOnlyForUnauthenticatedPlayer() {
        withServiceMock(listenerService)
            .check(arrowListener::onPlayerPickupArrow, PlayerPickupArrowEvent.class);
    }

    @Test
    void shouldCancelItemSwapOnlyForUnauthenticatedPlayer() {
        withServiceMock(listenerService)
            .check(swapListener::onPlayerSwapHandItems, PlayerSwapHandItemsEvent.class);
    }

    @Test
    void shouldCancelItemPickupOnlyForUnauthenticatedPlayer() {
        withServiceMock(listenerService)
            .check(pickupListener::onLegacyPlayerPickupItem, PlayerPickupItemEvent.class);
    }
}
