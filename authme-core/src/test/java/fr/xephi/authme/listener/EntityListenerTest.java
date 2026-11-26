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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

    @ParameterizedTest(name = "regain health, shouldCancel={0}")
    @ValueSource(booleans = {true, false})
    void shouldHandleRegainHealth(boolean shouldCancel) {
        EntityRegainHealthEvent event = mock(EntityRegainHealthEvent.class);
        given(listenerService.shouldCancelEvent(event)).willReturn(shouldCancel);

        listener.entityRegainHealthEvent(event);

        verify(listenerService).shouldCancelEvent(event);
        if (shouldCancel) {
            verify(event).setCancelled(true);
            verify(event).setAmount(0);
        } else {
            verifyNoInteractions(event);
        }
    }

    @ParameterizedTest(name = "entity damage by entity, shouldCancel={0}")
    @ValueSource(booleans = {true, false})
    void shouldHandleAttack(boolean shouldCancel) {
        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        Entity player = mock(Player.class);
        given(event.getDamager()).willReturn(player);
        given(listenerService.shouldCancelEvent(player)).willReturn(shouldCancel);

        listener.onAttack(event);

        verify(listenerService).shouldCancelEvent(player);
        if (shouldCancel) {
            verify(event).setCancelled(true);
        } else {
            verify(event, only()).getDamager();
        }
    }

    @ParameterizedTest(name = "entity damage, shouldCancel={0}")
    @ValueSource(booleans = {true, false})
    void shouldHandleDamage(boolean shouldCancel) {
        EntityDamageEvent event = mock(EntityDamageEvent.class);
        Entity entity = mock(Entity.class);
        if (shouldCancel) {
            given(event.getEntity()).willReturn(entity);
        }
        given(listenerService.shouldCancelEvent(event)).willReturn(shouldCancel);

        listener.onDamage(event);

        verify(listenerService).shouldCancelEvent(event);
        if (shouldCancel) {
            verify(event).setCancelled(true);
            verify(event).setDamage(0);
            verify(entity).setFireTicks(0);
        } else {
            verifyNoInteractions(event);
        }
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

    @ParameterizedTest(name = "projectile launch by player, shouldCancel={0}")
    @ValueSource(booleans = {true, false})
    void shouldHandleProjectileLaunchFromPlayer(boolean shouldCancel) {
        Projectile projectile = mock(Projectile.class);
        Player player = mock(Player.class);
        given(projectile.getShooter()).willReturn(player);
        ProjectileLaunchEvent event = mock(ProjectileLaunchEvent.class);
        given(event.getEntity()).willReturn(projectile);
        given(listenerService.shouldCancelEvent(player)).willReturn(shouldCancel);

        listener.onProjectileLaunch(event);

        verify(listenerService).shouldCancelEvent(player);
        if (shouldCancel) {
            verify(event).setCancelled(true);
        } else {
            verify(event, never()).setCancelled(anyBoolean());
        }
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

    @ParameterizedTest(name = "entity target, shouldCancel={0}")
    @ValueSource(booleans = {true, false})
    void shouldHandleEntityTarget(boolean shouldCancel) {
        EntityTargetEvent event = mock(EntityTargetEvent.class);
        Entity target = mock(Entity.class);
        given(event.getTarget()).willReturn(target);
        given(listenerService.shouldCancelEvent(target)).willReturn(shouldCancel);

        listener.onEntityTarget(event);

        verify(listenerService).shouldCancelEvent(target);
        if (shouldCancel) {
            verify(event).setCancelled(true);
        } else {
            verify(event, only()).getTarget();
        }
    }

    @Test
    void shouldCancelHangingBreakAndVehicleDamageByUnauthenticatedPlayer() {
        HangingBreakByEntityEvent breakEvent = mock(HangingBreakByEntityEvent.class);
        Entity remover = mock(Player.class);
        given(breakEvent.getRemover()).willReturn(remover);
        given(listenerService.shouldCancelEvent(remover)).willReturn(true);
        listener.onHangingBreakByEntity(breakEvent);
        verify(breakEvent).setCancelled(true);

        VehicleDamageEvent damageEvent = mock(VehicleDamageEvent.class);
        Entity attacker = mock(Player.class);
        given(damageEvent.getAttacker()).willReturn(attacker);
        given(listenerService.shouldCancelEvent(attacker)).willReturn(true);
        listener.onVehicleDamage(damageEvent);
        verify(damageEvent).setCancelled(true);
    }

    @ParameterizedTest(name = "vehicle destroy, shouldCancel={0}")
    @ValueSource(booleans = {true, false})
    void shouldHandleVehicleDestroy(boolean shouldCancel) {
        VehicleDestroyEvent event = mock(VehicleDestroyEvent.class);
        Entity attacker = mock(Player.class);
        given(event.getAttacker()).willReturn(attacker);
        given(listenerService.shouldCancelEvent(attacker)).willReturn(shouldCancel);

        listener.onVehicleDestroy(event);

        if (shouldCancel) {
            verify(event).setCancelled(true);
        } else {
            verify(event, never()).setCancelled(anyBoolean());
        }
    }
}
