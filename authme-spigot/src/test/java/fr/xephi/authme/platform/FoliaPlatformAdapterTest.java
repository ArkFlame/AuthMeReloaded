package fr.xephi.authme.platform;

import fr.xephi.authme.AuthMe;
import org.mockito.MockMakers;
import fr.xephi.authme.listener.BlockListener;
import fr.xephi.authme.listener.DoubleLoginFixListener;
import fr.xephi.authme.listener.LoginLocationFixListener;
import fr.xephi.authme.listener.EntityAirChangeListener;
import fr.xephi.authme.listener.EntityListener;
import fr.xephi.authme.listener.EntityPickupItemListener;
import fr.xephi.authme.listener.FoliaChatListener;
import fr.xephi.authme.listener.FoliaPlayerSpawnLocationListener;
import fr.xephi.authme.listener.PaperDialogFlowListener;
import fr.xephi.authme.listener.PaperProxyAutoLoginListener;
import fr.xephi.authme.listener.PaperLoginValidationListener;
import fr.xephi.authme.listener.PlayerListener;
import fr.xephi.authme.listener.PlayerOpenSignListener;
import fr.xephi.authme.listener.PlayerSwapHandItemsListener;
import fr.xephi.authme.listener.ServerListener;
import fr.xephi.authme.service.CancellableTask;
import io.papermc.paper.threadedregions.scheduler.AsyncScheduler;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class FoliaPlatformAdapterTest {

    private final FoliaPlatformAdapter adapter = new FoliaPlatformAdapter();

    @Test
    public void getPlatformNameReturnsExpectedValue() {
        assertThat(adapter.getPlatformName(), is("folia-1.21"));
    }

    @Test
    public void shouldNotBeSelectedOnPlainPaperClasspath() {
        // The unit-test classpath is Paper only (no RegionizedServer), so the merged jar must
        // leave the Folia adapter out and fall back to the Paper adapter.
        assertThat(adapter.getCompatibilityError() != null, is(true));
        assertThat(new PaperPlatformAdapter().getCompatibilityError(), is((String) null));
    }

    @Test
    public void getListenersContainsCoreAndFoliaListeners() {
        List<Class<? extends Listener>> listeners = adapter.getListeners();

        assertThat(listeners, containsInAnyOrder(
            PlayerListener.class,
            BlockListener.class,
            EntityListener.class,
            ServerListener.class,
            DoubleLoginFixListener.class,
            LoginLocationFixListener.class,
            PlayerSwapHandItemsListener.class,
            EntityAirChangeListener.class,
            EntityPickupItemListener.class,
            FoliaChatListener.class,
            PaperDialogFlowListener.class,
            PaperProxyAutoLoginListener.class,
            FoliaPlayerSpawnLocationListener.class,
            PaperLoginValidationListener.class,
            PlayerOpenSignListener.class));
    }

    @Test
    public void teleportPlayerWaitsForPendingTeleportOfSamePlayer() {
        // given
        Player player = mock(Player.class, withSettings().mockMaker(MockMakers.INLINE));
        given(player.getUniqueId()).willReturn(UUID.randomUUID());
        Location first = mock(Location.class, withSettings().mockMaker(MockMakers.INLINE));
        Location second = mock(Location.class, withSettings().mockMaker(MockMakers.INLINE));
        CompletableFuture<Boolean> firstTeleport = new CompletableFuture<>();
        given(player.teleportAsync(first)).willReturn(firstTeleport);
        given(player.teleportAsync(second)).willReturn(new CompletableFuture<>());
        EntityScheduler scheduler = mock(EntityScheduler.class, withSettings().mockMaker(MockMakers.INLINE));
        given(player.getScheduler()).willReturn(scheduler);
        given(scheduler.run(isNull(), any(), any())).willReturn(mock(ScheduledTask.class, withSettings().mockMaker(MockMakers.INLINE)));

        try (MockedStatic<JavaPlugin> javaPluginMock = mockStatic(JavaPlugin.class, withSettings().mockMaker(MockMakers.INLINE))) {
            javaPluginMock.when(() -> JavaPlugin.getPlugin(AuthMe.class)).thenReturn(null);

            // when
            adapter.teleportPlayer(player, first);
            adapter.teleportPlayer(player, second);

            // then
            verify(player, never()).teleportAsync(second);

            firstTeleport.complete(true);
            ArgumentCaptor<Consumer<ScheduledTask>> taskCaptor = ArgumentCaptor.captor();
            verify(scheduler).run(isNull(), taskCaptor.capture(), any());
            taskCaptor.getValue().accept(mock(ScheduledTask.class, withSettings().mockMaker(MockMakers.INLINE)));
            verify(player).teleportAsync(second);
        }
    }

    @Test
    public void runAsyncTaskUsesFoliaAsyncScheduler() {
        // given
        Runnable task = mock(Runnable.class, withSettings().mockMaker(MockMakers.INLINE));
        AsyncScheduler asyncScheduler = mock(AsyncScheduler.class, withSettings().mockMaker(MockMakers.INLINE));
        ScheduledTask scheduledTask = mock(ScheduledTask.class, withSettings().mockMaker(MockMakers.INLINE));
        given(asyncScheduler.runNow(isNull(), any())).willAnswer(invocation -> {
            invocation.<java.util.function.Consumer<ScheduledTask>>getArgument(1).accept(scheduledTask);
            return scheduledTask;
        });

        // when
        CancellableTask cancellableTask;
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class, withSettings().mockMaker(MockMakers.INLINE))) {
            bukkit.when(Bukkit::getAsyncScheduler).thenReturn(asyncScheduler);
            cancellableTask = adapter.runAsyncTask(null, task);
        }
        cancellableTask.cancel();

        // then
        verify(task).run();
        verify(asyncScheduler).runNow(isNull(), any());
        verify(scheduledTask).cancel();
    }

    @Test
    public void runAsyncTaskTimerConvertsTicksToMilliseconds() {
        // given
        Runnable task = mock(Runnable.class, withSettings().mockMaker(MockMakers.INLINE));
        AsyncScheduler asyncScheduler = mock(AsyncScheduler.class, withSettings().mockMaker(MockMakers.INLINE));
        ScheduledTask scheduledTask = mock(ScheduledTask.class, withSettings().mockMaker(MockMakers.INLINE));
        long delay = 4L;
        long period = 7L;
        given(asyncScheduler.runAtFixedRate(isNull(), any(), eq(200L), eq(350L), eq(TimeUnit.MILLISECONDS)))
            .willReturn(scheduledTask);

        // when
        CancellableTask cancellableTask;
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class, withSettings().mockMaker(MockMakers.INLINE))) {
            bukkit.when(Bukkit::getAsyncScheduler).thenReturn(asyncScheduler);
            cancellableTask = adapter.runAsyncTaskTimer(null, task, delay, period);
        }
        cancellableTask.cancel();

        // then
        verify(asyncScheduler).runAtFixedRate(isNull(), any(), eq(200L), eq(350L), eq(TimeUnit.MILLISECONDS));
        verify(scheduledTask).cancel();
    }

    @Test
    public void entityTasksUseAtLeastOneTickBecauseFoliaRejectsZero() {
        // given
        Entity entity = mock(Entity.class, withSettings().mockMaker(MockMakers.INLINE));
        EntityScheduler scheduler = mock(EntityScheduler.class, withSettings().mockMaker(MockMakers.INLINE));
        given(entity.getScheduler()).willReturn(scheduler);
        ScheduledTask scheduledTask = mock(ScheduledTask.class, withSettings().mockMaker(MockMakers.INLINE));
        given(scheduler.runDelayed(any(), any(), any(), anyLong())).willReturn(scheduledTask);
        given(scheduler.runAtFixedRate(any(), any(), any(), anyLong(), anyLong())).willReturn(scheduledTask);
        Runnable task = mock(Runnable.class, withSettings().mockMaker(MockMakers.INLINE));

        // when
        adapter.runDelayedOnEntityThread(null, entity, task, 0L);
        adapter.runAtFixedRateOnEntityThread(null, entity, task, 0L, 0L);

        // then
        verify(scheduler).runDelayed(isNull(), any(), isNull(), eq(1L));
        verify(scheduler).runAtFixedRate(isNull(), any(), isNull(), eq(1L), eq(1L));
    }

    @Test
    public void isOwnedByCurrentThreadDelegatesToRegionCheck() {
        // given
        Entity entity = mock(Entity.class, withSettings().mockMaker(MockMakers.INLINE));
        Location location = mock(Location.class, withSettings().mockMaker(MockMakers.INLINE));

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class, withSettings().mockMaker(MockMakers.INLINE))) {
            bukkit.when(() -> Bukkit.isOwnedByCurrentRegion(entity)).thenReturn(true);
            bukkit.when(() -> Bukkit.isOwnedByCurrentRegion(location)).thenReturn(false);

            // when / then
            assertThat(adapter.isOwnedByCurrentThread(entity), is(true));
            assertThat(adapter.isOwnedByCurrentThread(location), is(false));
        }
    }

    @Test
    public void concurrentTeleportOfSamePlayerWaitsForPendingTeleport() throws InterruptedException {
        // given
        Player player = mock(Player.class, withSettings().mockMaker(MockMakers.INLINE));
        given(player.getUniqueId()).willReturn(UUID.randomUUID());
        Location first = mock(Location.class, withSettings().mockMaker(MockMakers.INLINE));
        Location second = mock(Location.class, withSettings().mockMaker(MockMakers.INLINE));
        CompletableFuture<Boolean> firstTeleport = new CompletableFuture<>();
        AtomicInteger secondTeleportCount = new AtomicInteger();
        given(player.teleportAsync(first)).willReturn(firstTeleport);
        given(player.teleportAsync(second)).willAnswer(invocation -> {
            secondTeleportCount.incrementAndGet();
            return CompletableFuture.completedFuture(true);
        });
        EntityScheduler scheduler = mock(EntityScheduler.class, withSettings().mockMaker(MockMakers.INLINE));
        given(player.getScheduler()).willReturn(scheduler);
        given(scheduler.run(isNull(), any(), any())).willAnswer(invocation -> {
            invocation.<Consumer<ScheduledTask>>getArgument(1).accept(mock(ScheduledTask.class, withSettings().mockMaker(MockMakers.INLINE)));
            return mock(ScheduledTask.class, withSettings().mockMaker(MockMakers.INLINE));
        });

        try (MockedStatic<JavaPlugin> javaPluginMock = mockStatic(JavaPlugin.class, withSettings().mockMaker(MockMakers.INLINE))) {
            javaPluginMock.when(() -> JavaPlugin.getPlugin(AuthMe.class)).thenReturn(null);

            adapter.teleportPlayer(player, first);
            adapter.teleportPlayer(player, second);

            // then the second teleport must not start while the first one is still pending
            assertThat(secondTeleportCount.get(), is(0));

            firstTeleport.complete(true);
            assertThat(secondTeleportCount.get(), is(1));
        }
    }
}
