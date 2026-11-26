package fr.xephi.authme.events;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

/**
 * This event is fired before AuthMe teleports a player for general purposes.
 */
public class AuthMeTeleportEvent extends AbstractTeleportEvent {

    private static final HandlerList handlers = new HandlerList();

    /**
     * @param player the player, never {@code null}
     * @param to the teleport destination, or {@code null} to abort the teleport
     */
    public AuthMeTeleportEvent(Player player, Location to) {
        super(false, player, to);
    }

    /** Returns this event's handler list; never {@code null}. */
    public static HandlerList getHandlerList() {
        return handlers;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }
}
