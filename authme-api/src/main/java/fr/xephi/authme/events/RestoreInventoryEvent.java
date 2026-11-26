package fr.xephi.authme.events;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

/**
 * This event is fired when the inventory of a player is restored
 * (the inventory data is no longer hidden from the user).
 */
public class RestoreInventoryEvent extends AbstractCancellableEvent {

    private static final HandlerList handlers = new HandlerList();
    private final Player player;

    /**
     * @param player the player, never {@code null}
     */
    public RestoreInventoryEvent(Player player) {
        this.player = player;
    }

    /**
     * Return the player whose inventory will be restored.
     *
     * @return The player, never {@code null}
     */
    public Player getPlayer() {
        return player;
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
