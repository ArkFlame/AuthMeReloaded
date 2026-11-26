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
    void shouldCancelSimpleEventsOnlyForUnauthenticatedPlayer() {
        withServiceMock(listenerService)
            .check(airChangeListener::onPlayerAirChange, EntityAirChangeEvent.class)
            .check(arrowListener::onPlayerPickupArrow, PlayerPickupArrowEvent.class)
            .check(swapListener::onPlayerSwapHandItems, PlayerSwapHandItemsEvent.class)
            .check(pickupListener::onLegacyPlayerPickupItem, PlayerPickupItemEvent.class);
    }
}
