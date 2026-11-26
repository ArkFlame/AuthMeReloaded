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
     * Constructor.
     *
     * @param player The player
     * @param to The teleport destination
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
