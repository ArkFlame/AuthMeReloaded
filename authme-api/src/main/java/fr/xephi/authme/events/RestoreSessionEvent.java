package fr.xephi.authme.events;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

/**
 * Event fired before a session is restored.
 */
public class RestoreSessionEvent extends AbstractCancellableEvent {

    private static final HandlerList handlers = new HandlerList();
    private final Player player;

    /**
     * @param player the player whose session is about to be restored, never {@code null}
     * @param isAsync {@code true} to fire the event asynchronously, false otherwise
     */
    public RestoreSessionEvent(Player player, boolean isAsync) {
        super(isAsync);
        this.player = player;
    }

    /**
     * Return the player whose session is about to be restored.
     *
     * @return the player for which the session will be enabled, never {@code null}
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
