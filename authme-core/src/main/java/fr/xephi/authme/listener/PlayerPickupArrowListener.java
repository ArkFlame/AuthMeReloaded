package fr.xephi.authme.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPickupArrowEvent;

/**
 * Cancels arrow pickups of unauthenticated players. Kept apart from {@link PlayerListener} because
 * the event does not exist on legacy servers; it is only registered when the class is available.
 */
public class PlayerPickupArrowListener extends AbstractCancelListener implements Listener {

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onPlayerPickupArrow(PlayerPickupArrowEvent event) {
        cancelIfUnauthorized(event);
    }
}
