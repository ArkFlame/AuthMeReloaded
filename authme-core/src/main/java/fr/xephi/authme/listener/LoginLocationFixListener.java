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

/**
 * Moves players who log in inside a portal or underground to a safe location.
 *
 * <p>Ported from HaHaWTH/AuthMeReReloaded. Both fixes are opt-in and disabled by default.</p>
 */
public class LoginLocationFixListener implements Listener {

    private static final String[] PORTAL_MATERIAL_NAMES = {"PORTAL", "PORTAL_BLOCK", "NETHER_PORTAL"};
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
            if (isFreeSpot(adjacent)) {
                teleportAndNotify(player, adjacent, 0.1, MessageKey.LOCATION_FIX_PORTAL);
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

        int maxHeight = player.getWorld().getMaxHeight();
        for (int y = MIN_HEIGHT_AVAILABLE ? player.getWorld().getMinHeight() : 0; y <= maxHeight; y++) {
            joinLocation.setY(y);
            Block block = joinLocation.getBlock();
            Material belowType = block.getRelative(BlockFace.DOWN).getType();
            if (belowType.isBlock() && isFreeSpot(block)) {
                if (belowType == Material.LAVA) {
                    block.getRelative(BlockFace.DOWN).setType(Material.DIRT);
                }
                teleportAndNotify(player, block, 0.1, MessageKey.LOCATION_FIX_UNDERGROUND);
                return;
            }
        }
        teleportAndNotify(player, joinLocation.getBlock(), 1.1, MessageKey.LOCATION_FIX_UNDERGROUND_CANT_FIX);
    }

    private static boolean isFreeSpot(Block block) {
        return block.getType() == Material.AIR && block.getRelative(BlockFace.UP).getType() == Material.AIR;
    }

    private void teleportAndNotify(Player player, Block block, double yOffset, MessageKey message) {
        TeleportUtils.teleport(player, block.getLocation().add(0.5, yOffset, 0.5));
        commonService.send(player, message);
    }

    private static Material resolvePortalMaterial() {
        for (String name : PORTAL_MATERIAL_NAMES) {
            Material material = Material.matchMaterial(name);
            if (material != null) {
                return material;
            }
        }
        return null;
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
