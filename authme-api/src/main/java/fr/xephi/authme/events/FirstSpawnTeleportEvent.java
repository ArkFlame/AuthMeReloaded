package fr.xephi.authme.events;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

/**
 * Event that is called if a player is teleported to the AuthMe first spawn, i.e. to the
 * spawn location for players who have never played before.
 */
public class FirstSpawnTeleportEvent extends AbstractTeleportEvent {

    private static final HandlerList handlers = new HandlerList();

    /**
     * @param player the player, never {@code null}
     * @param to the teleport destination, or {@code null} to abort the teleport
     */
    public FirstSpawnTeleportEvent(Player player, Location to) {
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
