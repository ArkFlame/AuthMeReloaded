package fr.xephi.authme.events;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

/**
 * Event fired when a player enters a wrong password.
 */
public class FailedLoginEvent extends CustomEvent {

    private static final HandlerList handlers = new HandlerList();
    private final Player player;

    /**
     * Constructor.
     *
     * @param player The player
     * @param isAsync if the event is called asynchronously
     */
    public FailedLoginEvent(Player player, boolean isAsync) {
        super(isAsync);
        this.player = player;
    }

    /**
     * Return the player who entered a wrong password.
     *
     * @return The player entering a wrong password, never {@code null}
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
