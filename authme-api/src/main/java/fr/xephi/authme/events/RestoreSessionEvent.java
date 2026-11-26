package fr.xephi.authme.events;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

/**
 * Event fired before a session is restored.
 */
public class RestoreSessionEvent extends AbstractCancellableEvent {

    private static final HandlerList handlers = new HandlerList();
    private final Player player;

    /** Creates the event for the given player, never {@code null}. */
    public RestoreSessionEvent(Player player, boolean isAsync) {
        super(isAsync);
        this.player = player;
    }

    /** Returns the player whose session is about to be restored; never {@code null}. */
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
