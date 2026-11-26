package fr.xephi.authme.listener;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.entity.EntityEvent;
import org.bukkit.event.player.PlayerEvent;

import javax.inject.Inject;

/**
 * Shared base for listeners that cancel events for players who are not allowed to act
 * yet. The service lookup and the cancellation live here so that individual listeners
 * only need to declare their event handler.
 */
abstract class AbstractCancelListener {

    @Inject
    private ListenerService listenerService;

    protected final ListenerService getListenerService() {
        return listenerService;
    }

    /** Cancels a player event when its player is not allowed to act. */
    protected final void cancelIfUnauthorized(PlayerEvent event) {
        cancelEvent(listenerService.shouldCancelEvent(event), (Cancellable) event);
    }

    /** Cancels an entity event when its entity is not allowed to act. */
    protected final void cancelIfUnauthorized(EntityEvent event) {
        cancelEvent(listenerService.shouldCancelEvent(event), (Cancellable) event);
    }

    /** Cancels an event for the given player when that player is not allowed to act. */
    protected final void cancelIfUnauthorized(Player player, Cancellable event) {
        cancelEvent(listenerService.shouldCancelEvent(player), event);
    }

    /** Cancels an event for the given entity when it is a player who is not allowed to act. */
    protected final void cancelIfUnauthorized(Entity entity, Cancellable event) {
        cancelEvent(listenerService.shouldCancelEvent(entity), event);
    }

    protected final void cancelEvent(boolean shouldCancel, Cancellable event) {
        if (shouldCancel) {
            event.setCancelled(true);
        }
    }
}
