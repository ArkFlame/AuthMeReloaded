package fr.xephi.authme.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * This event is called when a player adds or changes his email address.
 */
public class EmailChangedEvent extends AbstractCancellableEvent {
    private static final HandlerList handlers = new HandlerList();
    private final Player player;
    private final String oldEmail;
    private final String newEmail;

    /**
     * Constructor.
     *
     * @param player The player that changed the email
     * @param oldEmail The old email the player had on file, or {@code null} when the player adds an email
     * @param newEmail The new email the player tries to set
     * @param isAsync whether this event should be called asynchronously
     */
    public EmailChangedEvent(Player player, String oldEmail, String newEmail, boolean isAsync) {
        super(isAsync);
        this.player = player;
        this.oldEmail = oldEmail;
        this.newEmail = newEmail;
    }

    /**
     * Returns the player who changed the email.
     *
     * @return The player who changed the email
     */
    public Player getPlayer() {
        return player;
    }

    /**
     * Gets the old email in case the user tries to change an existing email.
     *
     * @return The old email stored on file, or {@code null} when the user never had an email and adds a new one
     */
    public String getOldEmail() {
        return this.oldEmail;
    }

    /**
     * Gets the new email.
     *
     * @return The email the user is trying to set, never {@code null}; when the user adds an email and
     *         never had one before, this is where that email can be found
     */
    public String getNewEmail() {
        return this.newEmail;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    /**
     * Return the list of handlers, equivalent to {@link #getHandlers()} and required by {@link Event}.
     *
     * @return The list of handlers
     */
    public static HandlerList getHandlerList() {
        return handlers;
    }
}
