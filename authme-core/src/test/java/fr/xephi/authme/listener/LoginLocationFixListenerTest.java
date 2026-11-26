package fr.xephi.authme.listener;

import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.service.CommonService;
import fr.xephi.authme.settings.properties.SecuritySettings;
import fr.xephi.authme.util.TeleportUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockMakers;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Test for {@link LoginLocationFixListener}.
 */
@ExtendWith(MockitoExtension.class)
class LoginLocationFixListenerTest {

    @InjectMocks
    private LoginLocationFixListener listener;

    @Mock(mockMaker = MockMakers.INLINE)
    private CommonService commonService;

    @Test
    void shouldKeepSafeLocation() {
        given(commonService.getProperty(SecuritySettings.LOGIN_LOC_FIX_SUB_PORTAL)).willReturn(true);
        given(commonService.getProperty(SecuritySettings.LOGIN_LOC_FIX_SUB_UNDERGROUND)).willReturn(true);

        Player player = mock(Player.class, withSettings().mockMaker(MockMakers.INLINE));
        Location joinLocation = mock(Location.class, withSettings().mockMaker(MockMakers.INLINE));
        Block joinBlock = mock(Block.class, withSettings().mockMaker(MockMakers.INLINE));
        Block above = mock(Block.class, withSettings().mockMaker(MockMakers.INLINE));
        given(joinBlock.getType()).willReturn(Material.AIR);
        given(joinBlock.getRelative(BlockFace.UP)).willReturn(above);
        given(above.getType()).willReturn(Material.AIR);
        given(joinLocation.getBlock()).willReturn(joinBlock);
        given(player.getLocation()).willReturn(joinLocation);

        try (MockedStatic<TeleportUtils> teleportUtils = mockStatic(TeleportUtils.class, withSettings().mockMaker(MockMakers.INLINE))) {
            listener.onLoginLocationFix(eventFor(player));

            teleportUtils.verifyNoInteractions();
            verify(commonService, never()).send(any(Player.class), any(MessageKey.class));
        }
    }

    @Test
    void shouldMovePlayerStuckInPortal() {
        given(commonService.getProperty(SecuritySettings.LOGIN_LOC_FIX_SUB_PORTAL)).willReturn(true);
        given(commonService.getProperty(SecuritySettings.LOGIN_LOC_FIX_SUB_UNDERGROUND)).willReturn(false);

        Player player = mock(Player.class, withSettings().mockMaker(MockMakers.INLINE));
        Location joinLocation = mock(Location.class, withSettings().mockMaker(MockMakers.INLINE));
        Block joinBlock = mock(Block.class, withSettings().mockMaker(MockMakers.INLINE));
        Block above = mock(Block.class, withSettings().mockMaker(MockMakers.INLINE));
        Block neighbor = mock(Block.class, withSettings().mockMaker(MockMakers.INLINE));
        Block neighborAbove = mock(Block.class, withSettings().mockMaker(MockMakers.INLINE));
        given(joinLocation.getBlock()).willReturn(joinBlock);
        given(player.getLocation()).willReturn(joinLocation);
        given(joinBlock.getType()).willReturn(portalMaterial());
        given(joinBlock.getRelative(BlockFace.UP)).willReturn(above);
        given(joinBlock.getRelative(BlockFace.WEST)).willReturn(neighbor);
        given(neighbor.getType()).willReturn(Material.AIR);
        given(neighbor.getRelative(BlockFace.UP)).willReturn(neighborAbove);
        given(neighborAbove.getType()).willReturn(Material.AIR);
        given(neighbor.getLocation()).willReturn(new Location(mock(World.class, withSettings().mockMaker(MockMakers.INLINE)), 10, 64, 10));

        try (MockedStatic<TeleportUtils> teleportUtils = mockStatic(TeleportUtils.class, withSettings().mockMaker(MockMakers.INLINE))) {
            listener.onLoginLocationFix(eventFor(player));

            teleportUtils.verify(() -> TeleportUtils.teleport(eq(player), any(Location.class)));
            verify(commonService).send(player, MessageKey.LOCATION_FIX_PORTAL);
        }
    }

    @Test
    void shouldMovePlayerStuckUnderground() {
        given(commonService.getProperty(SecuritySettings.LOGIN_LOC_FIX_SUB_PORTAL)).willReturn(false);
        given(commonService.getProperty(SecuritySettings.LOGIN_LOC_FIX_SUB_UNDERGROUND)).willReturn(true);

        Player player = mock(Player.class, withSettings().mockMaker(MockMakers.INLINE));
        Location joinLocation = mock(Location.class, withSettings().mockMaker(MockMakers.INLINE));
        World world = mock(World.class, withSettings().mockMaker(MockMakers.INLINE));
        given(world.getMaxHeight()).willReturn(5);
        given(player.getWorld()).willReturn(world);
        given(player.getLocation()).willReturn(joinLocation);

        Block buried = mock(Block.class, withSettings().mockMaker(MockMakers.INLINE));
        Block buriedAbove = mock(Block.class, withSettings().mockMaker(MockMakers.INLINE));
        given(buried.getRelative(BlockFace.UP)).willReturn(buriedAbove);
        given(buriedAbove.getType()).willReturn(Material.STONE);

        Block candidate = mock(Block.class, withSettings().mockMaker(MockMakers.INLINE));
        Block solidBelow = mock(Block.class, withSettings().mockMaker(MockMakers.INLINE));
        Block airAbove = mock(Block.class, withSettings().mockMaker(MockMakers.INLINE));
        given(candidate.getRelative(BlockFace.DOWN)).willReturn(solidBelow);
        given(solidBelow.getType()).willReturn(Material.STONE);
        given(candidate.getType()).willReturn(Material.AIR);
        given(candidate.getRelative(BlockFace.UP)).willReturn(airAbove);
        given(airAbove.getType()).willReturn(Material.AIR);
        given(candidate.getLocation()).willReturn(new Location(world, 10, 64, 10));

        given(joinLocation.getBlock()).willReturn(buried, candidate);

        try (MockedStatic<TeleportUtils> teleportUtils = mockStatic(TeleportUtils.class, withSettings().mockMaker(MockMakers.INLINE))) {
            listener.onLoginLocationFix(eventFor(player));

            teleportUtils.verify(() -> TeleportUtils.teleport(eq(player), any(Location.class)));
            verify(commonService).send(player, MessageKey.LOCATION_FIX_UNDERGROUND);
        }
    }

    @Test
    void shouldDoNothingWhenBothFixesAreDisabled() {
        given(commonService.getProperty(SecuritySettings.LOGIN_LOC_FIX_SUB_PORTAL)).willReturn(false);
        given(commonService.getProperty(SecuritySettings.LOGIN_LOC_FIX_SUB_UNDERGROUND)).willReturn(false);

        Player player = mock(Player.class, withSettings().mockMaker(MockMakers.INLINE));

        try (MockedStatic<TeleportUtils> teleportUtils = mockStatic(TeleportUtils.class, withSettings().mockMaker(MockMakers.INLINE))) {
            listener.onLoginLocationFix(eventFor(player));

            verify(commonService).getProperty(SecuritySettings.LOGIN_LOC_FIX_SUB_PORTAL);
            verify(commonService).getProperty(SecuritySettings.LOGIN_LOC_FIX_SUB_UNDERGROUND);
            teleportUtils.verifyNoInteractions();
            verify(commonService, never()).send(any(Player.class), any(MessageKey.class));
        }
    }

    private static PlayerJoinEvent eventFor(Player player) {
        PlayerJoinEvent event = mock(PlayerJoinEvent.class, withSettings().mockMaker(MockMakers.INLINE));
        given(event.getPlayer()).willReturn(player);
        return event;
    }

    private static Material portalMaterial() {
        Material material = Material.matchMaterial("PORTAL");
        if (material == null) {
            material = Material.matchMaterial("PORTAL_BLOCK");
        }
        if (material == null) {
            material = Material.matchMaterial("NETHER_PORTAL");
        }
        return material;
    }
}
