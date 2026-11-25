package fr.xephi.authme.listener;

import fr.xephi.authme.service.CommonService;
import fr.xephi.authme.settings.properties.SecuritySettings;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.block.BlockDispenseEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Test for {@link AdvancedShulkerFixListener}.
 */
@ExtendWith(MockitoExtension.class)
class AdvancedShulkerFixListenerTest {

    @InjectMocks
    private AdvancedShulkerFixListener listener;

    @Mock
    private CommonService commonService;

    @Test
    void shouldCancelDispenseAtWorldFloor() {
        givenFixEnabled();
        BlockDispenseEvent event = eventAtHeight(0, 256);

        listener.onDispenserActivate(event);

        verify(event).setCancelled(true);
    }

    @Test
    void shouldCancelDispenseBelowWorldFloor() {
        givenFixEnabled();
        BlockDispenseEvent event = eventAtHeight(-1, 256);

        listener.onDispenserActivate(event);

        verify(event).setCancelled(true);
    }

    @Test
    void shouldCancelDispenseAtTopBoundary() {
        givenFixEnabled();
        BlockDispenseEvent event = eventAtHeight(255, 256);

        listener.onDispenserActivate(event);

        verify(event).setCancelled(true);
    }

    @Test
    void shouldCancelDispenseAboveTopBoundary() {
        givenFixEnabled();
        BlockDispenseEvent event = eventAtHeight(256, 256);

        listener.onDispenserActivate(event);

        verify(event).setCancelled(true);
    }

    @Test
    void shouldKeepDispenseJustAboveFloor() {
        givenFixEnabled();
        BlockDispenseEvent event = eventAtHeight(1, 256);

        listener.onDispenserActivate(event);

        verify(event, never()).setCancelled(anyBoolean());
    }

    @Test
    void shouldKeepDispenseJustBelowTopBoundary() {
        givenFixEnabled();
        BlockDispenseEvent event = eventAtHeight(254, 256);

        listener.onDispenserActivate(event);

        verify(event, never()).setCancelled(anyBoolean());
    }

    @Test
    void shouldKeepDispenseWhenFixIsDisabled() {
        given(commonService.getProperty(SecuritySettings.ADVANCED_SHULKER_FIX)).willReturn(false);
        BlockDispenseEvent event = eventAtHeight(0, 256);

        listener.onDispenserActivate(event);

        verify(event, never()).setCancelled(anyBoolean());
    }

    private void givenFixEnabled() {
        given(commonService.getProperty(SecuritySettings.ADVANCED_SHULKER_FIX)).willReturn(true);
    }

    private static BlockDispenseEvent eventAtHeight(int y, int maxHeight) {
        World world = mock(World.class);
        lenient().when(world.getMaxHeight()).thenReturn(maxHeight);
        Block block = mock(Block.class);
        lenient().when(block.getY()).thenReturn(y);
        lenient().when(block.getWorld()).thenReturn(world);
        BlockDispenseEvent event = mock(BlockDispenseEvent.class);
        lenient().when(event.getBlock()).thenReturn(block);
        return event;
    }
}
