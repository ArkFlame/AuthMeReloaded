package fr.xephi.authme.listener;

import fr.xephi.authme.service.CommonService;
import fr.xephi.authme.settings.properties.SecuritySettings;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.block.BlockDispenseEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockMakers;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Test for {@link AdvancedShulkerFixListener}.
 */
@ExtendWith(MockitoExtension.class)
class AdvancedShulkerFixListenerTest {

    @InjectMocks
    private AdvancedShulkerFixListener listener;

    @Mock(mockMaker = MockMakers.INLINE)
    private CommonService commonService;

    @ParameterizedTest(name = "dispense at y={0} cancelled={1}")
    @CsvSource({"0, true", "-1, true", "255, true", "256, true", "1, false", "254, false"})
    void shouldCancelDispenseOutsideValidHeightRange(int y, boolean cancelled) {
        givenFixEnabled();
        BlockDispenseEvent event = eventAtHeight(y);

        listener.onDispenserActivate(event);

        if (cancelled) {
            verify(event).setCancelled(true);
        } else {
            verify(event, never()).setCancelled(anyBoolean());
        }
    }

    @Test
    void shouldKeepDispenseWhenFixIsDisabled() {
        given(commonService.getProperty(SecuritySettings.ADVANCED_SHULKER_FIX)).willReturn(false);
        BlockDispenseEvent event = eventAtHeight(0);

        listener.onDispenserActivate(event);

        verify(event, never()).setCancelled(anyBoolean());
    }

    private void givenFixEnabled() {
        given(commonService.getProperty(SecuritySettings.ADVANCED_SHULKER_FIX)).willReturn(true);
    }

    private static BlockDispenseEvent eventAtHeight(int y) {
        World world = mock(World.class, withSettings().mockMaker(MockMakers.INLINE));
        lenient().when(world.getMaxHeight()).thenReturn(256);
        Block block = mock(Block.class, withSettings().mockMaker(MockMakers.INLINE));
        lenient().when(block.getY()).thenReturn(y);
        lenient().when(block.getWorld()).thenReturn(world);
        BlockDispenseEvent event = mock(BlockDispenseEvent.class, withSettings().mockMaker(MockMakers.INLINE));
        lenient().when(event.getBlock()).thenReturn(block);
        return event;
    }
}
