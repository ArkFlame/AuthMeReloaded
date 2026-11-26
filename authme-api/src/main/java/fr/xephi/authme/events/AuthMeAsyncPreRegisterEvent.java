package fr.xephi.authme.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * This event is called when a player uses the register command.
 * It is fired even when the player supplies invalid arguments.
 * {@link #setCanRegister(boolean) event.setCanRegister(false)} prevents the player from registering.
 */
public class AuthMeAsyncPreRegisterEvent extends CustomEvent {

    private static final HandlerList handlers = new HandlerList();
    private final Player player;
    private boolean canRegister = true;

    /**
     * Constructor.
     *
     * @param player The player
     * @param isAsync True if the event is async, false otherwise
     */
    public AuthMeAsyncPreRegisterEvent(Player player, boolean isAsync) {
        super(isAsync);
        this.player = player;
    }

    /**
     * Return the player concerned by this event.
     *
     * @return The player who executed the {@code /register} command
     */
    public Player getPlayer() {
        return player;
    }

    /**
     * Return whether the player is allowed to register.
     *
     * @return True if the player can register, false otherwise
     */
    public boolean canRegister() {
        return canRegister;
    }

    /**
     * Define whether or not the player may register.
     *
     * @param canRegister True to allow the player to register; false to prevent him
     */
    public void setCanRegister(boolean canRegister) {
        this.canRegister = canRegister;
    }

    /**
     * Return the list of handlers, equivalent to {@link #getHandlers()} and required by {@link Event}.
     *
     * @return The list of handlers
     */
    public static HandlerList getHandlerList() {
        return handlers;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

}
