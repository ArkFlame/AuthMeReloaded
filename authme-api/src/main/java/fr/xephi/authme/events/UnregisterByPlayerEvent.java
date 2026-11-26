package fr.xephi.authme.events;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

/**
 * Event fired after a player has unregistered himself.
 */
public class UnregisterByPlayerEvent extends AbstractUnregisterEvent {

    private static final HandlerList handlers = new HandlerList();

    /**
     * @param player the player (never null)
     * @param isAsync if the event is called asynchronously
     */
    public UnregisterByPlayerEvent(Player player, boolean isAsync) {
        super(player, isAsync);
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
