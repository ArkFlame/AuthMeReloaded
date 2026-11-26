package fr.xephi.authme.listener;

import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.projectiles.ProjectileSource;

import javax.inject.Inject;

public class EntityListener implements Listener {

    private final ListenerService listenerService;

    @Inject
    EntityListener(ListenerService listenerService) {
        this.listenerService = listenerService;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent event) {
        if (listenerService.shouldCancelEvent(event)) {
            event.getEntity().setFireTicks(0);
            event.setDamage(0);
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (listenerService.shouldCancelEvent(event.getDamager())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onEntityTarget(EntityTargetEvent event) {
        if (listenerService.shouldCancelEvent(event.getTarget())) {
            event.setTarget(null);
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        cancelIfUnauthorized(event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void entityRegainHealthEvent(EntityRegainHealthEvent event) {
        if (listenerService.shouldCancelEvent(event)) {
            event.setAmount(0);
            event.setCancelled(true);
        }
    }

    // Both priorities are needed: the LOWEST handler blocks early, the HIGHEST one
    // re-applies the check after other plugins have had a chance to uncancel.
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onEntityInteract(EntityInteractEvent event) {
        cancelIfUnauthorized(event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onLowestEntityInteract(EntityInteractEvent event) {
        cancelIfUnauthorized(event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        final Projectile projectile = event.getEntity();

        ProjectileSource shooter = projectile.getShooter();
        if (shooter instanceof Player && listenerService.shouldCancelEvent((Player) shooter)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onShoot(EntityShootBowEvent event) {
        cancelIfUnauthorized(event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onHangingBreakByEntity(HangingBreakByEntityEvent event) {
        if (listenerService.shouldCancelEvent(event.getRemover())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onVehicleDamage(VehicleDamageEvent event) {
        if (listenerService.shouldCancelEvent(event.getAttacker())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        if (listenerService.shouldCancelEvent(event.getAttacker())) {
            event.setCancelled(true);
        }
    }

    private void cancelIfUnauthorized(EntityEvent event) {
        if (listenerService.shouldCancelEvent(event)) {
            ((Cancellable) event).setCancelled(true);
        }
    }
}
