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
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import javax.inject.Inject;
import java.lang.reflect.Method;

/**
 * Moves players who log in inside a portal or underground to a safe location.
 *
 * <p>Ported from HaHaWTH/AuthMeReReloaded. Both fixes are opt-in and disabled by default.</p>
 */
public class LoginLocationFixListener implements Listener {

    private static final Material PORTAL_MATERIAL = resolvePortalMaterial();
    private static final boolean MIN_HEIGHT_AVAILABLE = hasMinHeight();
    private static final BlockFace[] NEIGHBOR_FACES = {
        BlockFace.WEST, BlockFace.EAST, BlockFace.NORTH, BlockFace.SOUTH,
        BlockFace.SOUTH_EAST, BlockFace.SOUTH_WEST, BlockFace.NORTH_EAST, BlockFace.NORTH_WEST
    };

    @Inject
    private CommonService commonService;

    @EventHandler
    public void onLoginLocationFix(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Location joinLocation = player.getLocation();

        if (commonService.getProperty(SecuritySettings.LOGIN_LOC_FIX_SUB_PORTAL)) {
            fixPortalStuck(player, joinLocation);
        }
        if (commonService.getProperty(SecuritySettings.LOGIN_LOC_FIX_SUB_UNDERGROUND)) {
            fixUndergroundStuck(player, joinLocation);
        }
    }

    private void fixPortalStuck(Player player, Location joinLocation) {
        Block joinBlock = joinLocation.getBlock();
        Block above = joinBlock.getRelative(BlockFace.UP);
        if (joinBlock.getType() != PORTAL_MATERIAL && above.getType() != PORTAL_MATERIAL) {
            return;
        }

        for (BlockFace face : NEIGHBOR_FACES) {
            Block adjacent = joinBlock.getRelative(face);
            if (adjacent.getType() == Material.AIR && adjacent.getRelative(BlockFace.UP).getType() == Material.AIR) {
                TeleportUtils.teleport(player, adjacent.getLocation().add(0.5, 0.1, 0.5));
                commonService.send(player, MessageKey.LOCATION_FIX_PORTAL);
                return;
            }
        }

        // No free spot nearby: break the portal blocks so the player is not trapped.
        above.breakNaturally();
        joinBlock.breakNaturally();
        commonService.send(player, MessageKey.LOCATION_FIX_PORTAL);
    }

    private void fixUndergroundStuck(Player player, Location joinLocation) {
        Material aboveType = joinLocation.getBlock().getRelative(BlockFace.UP).getType();
        if (!aboveType.isOccluding() && aboveType != Material.LAVA) {
            return;
        }

        World world = player.getWorld();
        int maxHeight = world.getMaxHeight();
        int minHeight = getMinHeight(world);
        for (int y = minHeight; y <= maxHeight; y++) {
            joinLocation.setY(y);
            Block block = joinLocation.getBlock();
            Block below = block.getRelative(BlockFace.DOWN);
            if (below.getType().isBlock() && block.getType() == Material.AIR
                && block.getRelative(BlockFace.UP).getType() == Material.AIR) {
                if (below.getType() == Material.LAVA) {
                    below.setType(Material.DIRT);
                }
                TeleportUtils.teleport(player, block.getLocation().add(0.5, 0.1, 0.5));
                commonService.send(player, MessageKey.LOCATION_FIX_UNDERGROUND);
                return;
            }
            if (y == maxHeight) {
                TeleportUtils.teleport(player, block.getLocation().add(0.5, 1.1, 0.5));
                commonService.send(player, MessageKey.LOCATION_FIX_UNDERGROUND_CANT_FIX);
            }
        }
    }

    private static int getMinHeight(World world) {
        return MIN_HEIGHT_AVAILABLE ? world.getMinHeight() : 0;
    }

    private static Material resolvePortalMaterial() {
        Material material = Material.matchMaterial("PORTAL");
        if (material == null) {
            material = Material.matchMaterial("PORTAL_BLOCK");
        }
        if (material == null) {
            material = Material.matchMaterial("NETHER_PORTAL");
        }
        return material;
    }

    private static boolean hasMinHeight() {
        try {
            World.class.getMethod("getMinHeight");
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }
}
