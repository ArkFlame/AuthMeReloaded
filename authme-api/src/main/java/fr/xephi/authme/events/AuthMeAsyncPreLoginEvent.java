package fr.xephi.authme.events;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

/**
 * This event is called when a player uses the login command.
 * It is fired even when the player supplies an invalid password.
 * {@link #setCanLogin(boolean) event.setCanLogin(false)} prevents the player from logging in.
 */
public class AuthMeAsyncPreLoginEvent extends CustomEvent {

    private static final HandlerList handlers = new HandlerList();
    private final Player player;
    private boolean canLogin = true;

    /**
     * @param player the player, never {@code null}
     * @param isAsync True if the event is async, false otherwise
     */
    public AuthMeAsyncPreLoginEvent(Player player, boolean isAsync) {
        super(isAsync);
        this.player = player;
    }

    /**
     * Return the player who issued the login request.
     *
     * @return the player who executed the {@code /login} command, never {@code null}
     */
    public Player getPlayer() {
        return player;
    }

    /**
     * Return whether the player is allowed to log in.
     *
     * @return True if the player can log in, false otherwise
     */
    public boolean canLogin() {
        return canLogin;
    }

    /**
     * Define whether or not the player may log in.
     *
     * @param canLogin True to allow the player to log in; false to prevent him
     */
    public void setCanLogin(boolean canLogin) {
        this.canLogin = canLogin;
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
