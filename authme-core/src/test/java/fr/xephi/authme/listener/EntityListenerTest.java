package fr.xephi.authme.listener;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static fr.xephi.authme.listener.EventCancelVerifier.withServiceMock;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Test for {@link EntityListener}.
 */
@ExtendWith(MockitoExtension.class)
class EntityListenerTest {

    @InjectMocks
    private EntityListener listener;

    @Mock
    private ListenerService listenerService;

    @Test
    void shouldHandleSimpleEvents() {
        withServiceMock(listenerService)
            .check(listener::onFoodLevelChange, FoodLevelChangeEvent.class)
            .check(listener::onShoot, EntityShootBowEvent.class)
            .check(listener::onEntityInteract, EntityInteractEvent.class)
            .check(listener::onLowestEntityInteract, EntityInteractEvent.class);
    }

    @Test
    void shouldCancelRegainHealthEvent() {
        EntityRegainHealthEvent event = mock(EntityRegainHealthEvent.class);
        given(listenerService.shouldCancelEvent(event)).willReturn(true);

        listener.entityRegainHealthEvent(event);

        verify(listenerService).shouldCancelEvent(event);
        verify(event).setCancelled(true);
        verify(event).setAmount(0);
    }

    @Test
    void shouldNotCancelRegainedHealth() {
        EntityRegainHealthEvent event = mock(EntityRegainHealthEvent.class);
        given(listenerService.shouldCancelEvent(event)).willReturn(false);

        listener.entityRegainHealthEvent(event);

        verify(listenerService).shouldCancelEvent(event);
        verifyNoInteractions(event);
    }

    @Test
    void shouldCancelEntityDamageByEntityEvent() {
        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        Entity player = mock(Player.class);
        given(event.getDamager()).willReturn(player);
        given(listenerService.shouldCancelEvent(player)).willReturn(true);

        listener.onAttack(event);

        verify(listenerService).shouldCancelEvent(player);
        verify(event).setCancelled(true);
    }

    @Test
    void shouldNotCancelEntityDamageByEntityEvent() {
        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        Entity player = mock(Player.class);
        given(event.getDamager()).willReturn(player);
        given(listenerService.shouldCancelEvent(player)).willReturn(false);

        listener.onAttack(event);

        verify(listenerService).shouldCancelEvent(player);
        verify(event, only()).getDamager();
    }

    @Test
    void shouldCancelEntityDamageEvent() {
        EntityDamageEvent event = mock(EntityDamageEvent.class);
        Entity entity = mock(Entity.class);
        given(event.getEntity()).willReturn(entity);
        given(listenerService.shouldCancelEvent(event)).willReturn(true);

        listener.onDamage(event);

        verify(listenerService).shouldCancelEvent(event);
        verify(event).setCancelled(true);
        verify(event).setDamage(0);
        verify(entity).setFireTicks(0);
    }

    @Test
    void shouldNotCancelEntityDamageEvent() {
        EntityDamageEvent event = mock(EntityDamageEvent.class);
        given(listenerService.shouldCancelEvent(event)).willReturn(false);

        listener.onDamage(event);

        verify(listenerService).shouldCancelEvent(event);
        verifyNoInteractions(event);
    }

    @Test
    void shouldAllowProjectileLaunchFromNonHuman() {
        Projectile projectile = mock(Projectile.class);
        ProjectileSource source = mock(ProjectileSource.class);
        given(projectile.getShooter()).willReturn(source);
        ProjectileLaunchEvent event = mock(ProjectileLaunchEvent.class);
        given(event.getEntity()).willReturn(projectile);

        listener.onProjectileLaunch(event);

        verifyNoInteractions(listenerService);
        verify(event, never()).setCancelled(anyBoolean());
    }

    @Test
    void shouldAllowProjectileLaunchFromAuthedHuman() {
        Projectile projectile = mock(Projectile.class);
        Player player = mock(Player.class);
        given(projectile.getShooter()).willReturn(player);
        ProjectileLaunchEvent event = mock(ProjectileLaunchEvent.class);
        given(event.getEntity()).willReturn(projectile);
        given(listenerService.shouldCancelEvent(player)).willReturn(false);

        listener.onProjectileLaunch(event);

        verify(listenerService).shouldCancelEvent(player);
        verify(event, never()).setCancelled(anyBoolean());
    }

    @Test
    void shouldRejectProjectileLaunchFromUnauthed() {
        Projectile projectile = mock(Projectile.class);
        Player player = mock(Player.class);
        given(projectile.getShooter()).willReturn(player);
        ProjectileLaunchEvent event = mock(ProjectileLaunchEvent.class);
        given(event.getEntity()).willReturn(projectile);
        given(listenerService.shouldCancelEvent(player)).willReturn(true);

        listener.onProjectileLaunch(event);

        verify(listenerService).shouldCancelEvent(player);
        verify(event).setCancelled(true);
    }

    @Test
    void shouldHandleOldShooterMethod() {
        Projectile projectile = mock(Projectile.class);
        Player shooter = mock(Player.class);
        given(projectile.getShooter()).willReturn(shooter);
        ProjectileLaunchEvent event = new ProjectileLaunchEvent(projectile);
        given(listenerService.shouldCancelEvent(shooter)).willReturn(true);

        listener.onProjectileLaunch(event);

        verify(listenerService).shouldCancelEvent(shooter);
        assertThat(event.isCancelled(), equalTo(true));
    }

    @Test
    void shouldCancelEntityTargetEvent() {
        EntityTargetEvent event = mock(EntityTargetEvent.class);
        Entity target = mock(Entity.class);
        given(event.getTarget()).willReturn(target);
        given(listenerService.shouldCancelEvent(target)).willReturn(true);

        listener.onEntityTarget(event);

        verify(listenerService).shouldCancelEvent(target);
        verify(event).setCancelled(true);
    }

    @Test
    void shouldNotCancelEntityTargetEvent() {
        EntityTargetEvent event = mock(EntityTargetEvent.class);
        Entity target = mock(Entity.class);
        given(event.getTarget()).willReturn(target);
        given(listenerService.shouldCancelEvent(target)).willReturn(false);

        listener.onEntityTarget(event);

        verify(listenerService).shouldCancelEvent(target);
        verify(event, only()).getTarget();
    }

    @Test
    void shouldCancelHangingBreakByUnauthenticatedPlayer() {
        HangingBreakByEntityEvent event = mock(HangingBreakByEntityEvent.class);
        Entity remover = mock(Player.class);
        given(event.getRemover()).willReturn(remover);
        given(listenerService.shouldCancelEvent(remover)).willReturn(true);

        listener.onHangingBreakByEntity(event);

        verify(event).setCancelled(true);
    }

    @Test
    void shouldCancelVehicleDamageByUnauthenticatedPlayer() {
        VehicleDamageEvent event = mock(VehicleDamageEvent.class);
        Entity attacker = mock(Player.class);
        given(event.getAttacker()).willReturn(attacker);
        given(listenerService.shouldCancelEvent(attacker)).willReturn(true);

        listener.onVehicleDamage(event);

        verify(event).setCancelled(true);
    }

    @Test
    void shouldCancelVehicleDestroyByUnauthenticatedPlayer() {
        VehicleDestroyEvent event = mock(VehicleDestroyEvent.class);
        Entity attacker = mock(Player.class);
        given(event.getAttacker()).willReturn(attacker);
        given(listenerService.shouldCancelEvent(attacker)).willReturn(true);

        listener.onVehicleDestroy(event);

        verify(event).setCancelled(true);
    }

    @Test
    void shouldNotCancelVehicleDestroyByAuthenticatedPlayer() {
        VehicleDestroyEvent event = mock(VehicleDestroyEvent.class);
        Entity attacker = mock(Player.class);
        given(event.getAttacker()).willReturn(attacker);
        given(listenerService.shouldCancelEvent(attacker)).willReturn(false);

        listener.onVehicleDestroy(event);

        verify(event, never()).setCancelled(anyBoolean());
    }
}
