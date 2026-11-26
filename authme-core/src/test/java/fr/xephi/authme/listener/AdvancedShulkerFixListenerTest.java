package fr.xephi.authme.listener;

import fr.xephi.authme.service.CommonService;
import fr.xephi.authme.settings.properties.SecuritySettings;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.block.BlockDispenseEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

    @ParameterizedTest(name = "cancel dispense at y={0}")
    @ValueSource(ints = {0, -1, 255, 256})
    void shouldCancelDispenseOutsideValidHeightRange(int y) {
        givenFixEnabled();
        BlockDispenseEvent event = eventAtHeight(y, 256);

        listener.onDispenserActivate(event);

        verify(event).setCancelled(true);
    }

    @ParameterizedTest(name = "allow dispense at y={0}")
    @ValueSource(ints = {1, 254})
    void shouldKeepDispenseInsideValidHeightRange(int y) {
        givenFixEnabled();
        BlockDispenseEvent event = eventAtHeight(y, 256);

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
