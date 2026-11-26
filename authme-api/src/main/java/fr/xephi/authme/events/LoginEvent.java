package fr.xephi.authme.events;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

/**
 * Event fired when a player has successfully logged in or registered.
 */
public class LoginEvent extends CustomEvent {

    private static final HandlerList handlers = new HandlerList();
    private final Player player;

    /**
     * Constructor.
     *
     * @param player The player
     */
    public LoginEvent(Player player) {
        this.player = player;
    }

    /**
     * Return the player that has successfully logged in or registered.
     *
     * @return The player, never {@code null}
     */
    public Player getPlayer() {
        return player;
    }

    /**
     * Ensures compatibility with plugins like GuiRules.
     *
     * @return true
     * @deprecated this will always return true because this event is only called if it was successful
     */
    @Deprecated
    public boolean isLogin() {
        return true;
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
