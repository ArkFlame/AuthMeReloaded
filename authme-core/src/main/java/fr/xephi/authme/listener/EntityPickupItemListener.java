package fr.xephi.authme.listener;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;

public class EntityPickupItemListener extends AbstractCancelListener implements Listener {

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onPlayerPickupItem(EntityPickupItemEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof Player) {
            cancelIfUnauthorized((Player) entity, event);
        }
    }
}
