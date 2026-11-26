package fr.xephi.authme.listener;

import fr.xephi.authme.service.CommonService;
import fr.xephi.authme.settings.properties.SecuritySettings;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;

import javax.inject.Inject;

/**
 * Prevents the shulker crash by cancelling dispenses outside the valid world height range.
 *
 * <p>Ported from HaHaWTH/AuthMeReReloaded; original header: "This fix is only for
 * Minecraft 1.13-". In this build the platform adapter registers it on 1.8 only.</p>
 */
public class AdvancedShulkerFixListener implements Listener {

    @Inject
    private CommonService commonService;

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDispenserActivate(BlockDispenseEvent event) {
        if (commonService != null && !commonService.getProperty(SecuritySettings.ADVANCED_SHULKER_FIX)) {
            return;
        }

        Block block = event.getBlock();
        int y = block.getY();
        if (y <= 0 || y >= block.getWorld().getMaxHeight() - 1) {
            event.setCancelled(true);
        }
    }
}
