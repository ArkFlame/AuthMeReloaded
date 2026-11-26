package fr.xephi.authme.events;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

/**
 * Called if a player is teleported to a specific spawn upon joining or logging in.
 */
public class SpawnTeleportEvent extends AbstractTeleportEvent {

    private static final HandlerList handlers = new HandlerList();
    private final boolean isAuthenticated;

    /**
     * @param player the player, never {@code null}
     * @param to the teleport destination, or {@code null} to abort the teleport
     * @param isAuthenticated Whether or not the player is logged in
     */
    public SpawnTeleportEvent(Player player, Location to, boolean isAuthenticated) {
        super(false, player, to);
        this.isAuthenticated = isAuthenticated;
    }

    /**
     * Return whether or not the player is authenticated.
     *
     * @return true if the player is logged in, false otherwise
     */
    public boolean isAuthenticated() {
        return isAuthenticated;
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
