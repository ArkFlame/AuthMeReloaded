package fr.xephi.authme.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Test for {@link BlockListener}.
 */
@ExtendWith(MockitoExtension.class)
class BlockListenerTest {

    @InjectMocks
    private BlockListener listener;

    @Mock
    private ListenerService listenerService;

    @ParameterizedTest(name = "block place, shouldCancel={0}")
    @ValueSource(booleans = {false, true})
    void shouldHandlePlaceEvent(boolean shouldCancel) {
        Player player = mock(Player.class);
        BlockPlaceEvent event = mock(BlockPlaceEvent.class);
        given(event.getPlayer()).willReturn(player);
        given(listenerService.shouldCancelEvent(player)).willReturn(shouldCancel);

        listener.onBlockPlace(event);

        if (shouldCancel) {
            verify(event).setCancelled(true);
        }
        verify(event).getPlayer();
        verifyNoMoreInteractions(event);
    }

    @ParameterizedTest(name = "block break, shouldCancel={0}")
    @ValueSource(booleans = {false, true})
    void shouldHandleBreakEvent(boolean shouldCancel) {
        Player player = mock(Player.class);
        BlockBreakEvent event = mock(BlockBreakEvent.class);
        given(event.getPlayer()).willReturn(player);
        given(listenerService.shouldCancelEvent(player)).willReturn(shouldCancel);

        listener.onBlockBreak(event);

        if (shouldCancel) {
            verify(event).setCancelled(true);
        }
        verify(event).getPlayer();
        verifyNoMoreInteractions(event);
    }

}
