package fr.xephi.authme.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

public class BlockListener extends AbstractCancelListener implements Listener {

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) { cancelIfUnauthorized(event.getPlayer(), event); }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) { cancelIfUnauthorized(event.getPlayer(), event); }
}
