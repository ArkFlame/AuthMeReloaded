package fr.xephi.authme.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPickupItemEvent;

public class LegacyPlayerPickupItemListener extends AbstractCancelListener implements Listener {

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onLegacyPlayerPickupItem(PlayerPickupItemEvent event) {
        cancelIfUnauthorized(event);
    }
}
