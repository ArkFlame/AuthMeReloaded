package fr.xephi.authme.events;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

/**
 * Event fired when a player has successfully registered.
 */
public class RegisterEvent extends CustomEvent {

    private static final HandlerList handlers = new HandlerList();
    private final Player player;

    /**
     * @param player the player, never {@code null}
     */
    public RegisterEvent(Player player) {
        this.player = player;
    }

    /**
     * Return the player that has successfully registered.
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
