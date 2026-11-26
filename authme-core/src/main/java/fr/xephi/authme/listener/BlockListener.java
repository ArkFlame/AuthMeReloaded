package fr.xephi.authme.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import javax.inject.Inject;

public class BlockListener implements Listener {

    @Inject
    private ListenerService listenerService;

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        cancelIfUnauthorized(event.getPlayer(), event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        cancelIfUnauthorized(event.getPlayer(), event);
    }

    private void cancelIfUnauthorized(Player player, Cancellable event) {
        if (listenerService.shouldCancelEvent(player)) {
            event.setCancelled(true);
        }
    }
}
