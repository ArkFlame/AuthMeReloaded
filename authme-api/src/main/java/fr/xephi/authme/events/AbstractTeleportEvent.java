package fr.xephi.authme.events;

import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Common supertype for all AuthMe teleport events.
 * <p>
 * The player and the origin of the teleport are fixed when the event is created. Listeners may change the
 * destination with {@link #setTo(Location)}; a {@code null} destination aborts the teleport.
 * <p>
 * The destination is written by listeners on the thread that fires the event. Integrations that retain the
 * event must read {@link #getTo()} on that same thread: the event gives no cross-thread visibility guarantee
 * for state changed by listeners.
 */
public abstract class AbstractTeleportEvent extends AbstractCancellableEvent {

    private final Player player;
    private final Location from;
    private Location to;

    /**
     * @param isAsync whether to fire the event asynchronously
     * @param player the player, never {@code null}
     * @param to the teleport destination, or {@code null} to abort the teleport
     */
    public AbstractTeleportEvent(boolean isAsync, Player player, Location to) {
        super(isAsync);
        this.player = player;
        this.from = player.getLocation();
        this.to = to;
    }

    /**
     * Return the player planned to be teleported.
     *
     * @return The player, never {@code null}
     */
    public Player getPlayer() {
        return player;
    }

    /**
     * Return the location the player is being teleported away from.
     * The location is captured when the event is created; treat it as read-only.
     *
     * @return The location prior to the teleport, never {@code null}
     */
    public Location getFrom() {
        return from;
    }

    /**
     * Set the destination of the teleport.
     *
     * @param to The location to teleport the player to, or {@code null} to abort the teleport
     */
    public void setTo(Location to) {
        this.to = to;
    }

    /**
     * Return the destination the player is being teleported to. Listeners set this value on the firing thread;
     * read it on that thread, as no cross-thread visibility guarantee is provided.
     *
     * @return The teleport destination, or {@code null} if a listener cleared it
     */
    public Location getTo() {
        return to;
    }
}
