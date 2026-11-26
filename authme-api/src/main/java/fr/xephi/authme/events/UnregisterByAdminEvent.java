package fr.xephi.authme.events;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

/**
 * Event fired after a player name has been unregistered by an admin or via the API.
 * <p>
 * Only {@code playerName} is guaranteed to be non-null. The {@code player} may be null for a name the
 * server has never seen online, and the {@code initiator} is null when the unregister came from the API
 * instead of a command.
 */
public class UnregisterByAdminEvent extends AbstractUnregisterEvent {

    private static final HandlerList handlers = new HandlerList();
    private final String playerName;
    private final CommandSender initiator;

    /**
     * @param player the player, may be null (see class JavaDoc)
     * @param playerName the unregistered name
     * @param isAsync whether the event is async
     * @param initiator the initiator, may be null (see class JavaDoc)
     */
    public UnregisterByAdminEvent(Player player, String playerName, boolean isAsync, CommandSender initiator) {
        super(player, isAsync);
        this.playerName = playerName;
        this.initiator = initiator;
    }

    /**
     * Returns the name of the player that was unregistered.
     *
     * @return the unregistered name, never {@code null}
     */
    public String getPlayerName() {
        return playerName;
    }

    /**
     * Returns the user who requested the unregister, if any.
     *
     * @return the initiator, or {@code null} when the unregister came from the API
     */
    public CommandSender getInitiator() {
        return initiator;
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
