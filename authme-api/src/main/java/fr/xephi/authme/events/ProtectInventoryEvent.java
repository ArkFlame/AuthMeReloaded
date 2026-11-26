package fr.xephi.authme.events;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

/**
 * This event is called before the inventory data of a player is suppressed,
 * i.e. the inventory of the player is not displayed until he has authenticated.
 */
public class ProtectInventoryEvent extends AbstractCancellableEvent {

    private static final HandlerList handlers = new HandlerList();
    private final ItemStack[] storedInventory;
    private final ItemStack[] storedArmor;
    private final Player player;

    /**
     * @param player the player, never {@code null}
     * @param isAsync True if the event is async, false otherwise
     */
    public ProtectInventoryEvent(Player player, boolean isAsync) {
        super(isAsync);
        this.player = player;
        this.storedInventory = player.getInventory().getContents();
        this.storedArmor = player.getInventory().getArmorContents();
    }

    /**
     * Return the inventory of the player as it was when the event was created.
     * Treat the array as read-only: it is a snapshot shared with other listeners.
     *
     * @return A snapshot of the player's inventory, never {@code null}
     */
    public ItemStack[] getStoredInventory() {
        return storedInventory;
    }

    /**
     * Return the armor of the player as it was when the event was created.
     * Treat the array as read-only: it is a snapshot shared with other listeners.
     *
     * @return A snapshot of the player's armor, never {@code null}
     */
    public ItemStack[] getStoredArmor() {
        return storedArmor;
    }

    /**
     * Return the player whose inventory will be hidden.
     *
     * @return The player associated with this event, never {@code null}
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
