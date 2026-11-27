package fr.xephi.authme.service;

import fr.xephi.authme.AuthMe;
import fr.xephi.authme.ReflectionTestUtils;
import fr.xephi.authme.events.FailedLoginEvent;
import fr.xephi.authme.platform.SchedulingAdapter;
import fr.xephi.authme.settings.Settings;
import fr.xephi.authme.settings.properties.PluginSettings;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Test for {@link BukkitService}.
 */
@ExtendWith(MockitoExtension.class)
class BukkitServiceTest {

    private BukkitService bukkitService;

    @Mock
    private AuthMe authMe;
    @Mock
    private Settings settings;
    @Mock
    private Server server;
    @Mock
    private BukkitScheduler scheduler;
    @Mock
    private PluginManager pluginManager;
    @Mock
    private SchedulingAdapter schedulingAdapter;

    @BeforeEach
    void constructBukkitService() {
        ReflectionTestUtils.setField(Bukkit.class, null, "server", server);
        given(settings.getProperty(PluginSettings.USE_ASYNC_TASKS)).willReturn(true);
        bukkitService = new BukkitService(authMe, settings, schedulingAdapter);
    }

    @Test
    void shouldDispatchCommand() {
        // given
        CommandSender sender = mock(CommandSender.class);
        String command = "help test abc";

        // when
        bukkitService.dispatchCommand(sender, command);

        // then
        verify(server).dispatchCommand(sender, command);
    }

    @Test
    void shouldDispatchConsoleCommand() {
        // given
        ConsoleCommandSender consoleSender = mock(ConsoleCommandSender.class);
        given(server.getConsoleSender()).willReturn(consoleSender);
        given(schedulingAdapter.isGlobalThread()).willReturn(true);
        String command = "my command";

        // when
        bukkitService.dispatchConsoleCommand(command);

        // then
        verify(server).dispatchCommand(consoleSender, command);
    }

    @Test
    void shouldDispatchConsoleCommandOnGlobalThread() {
        // given
        given(schedulingAdapter.isGlobalThread()).willReturn(false);

        // when
        bukkitService.dispatchConsoleCommand("my command");

        // then
        verify(schedulingAdapter).runOnGlobalThread(eq(authMe), any(Runnable.class));
        verify(server, never()).dispatchCommand(any(), any());
    }

    @ParameterizedTest(name = "global thread: {0}")
    @ValueSource(booleans = {true, false})
    void shouldScheduleSyncTask(boolean isGlobalThread) {
        Runnable task = mock(Runnable.class);
        given(schedulingAdapter.isGlobalThread()).willReturn(isGlobalThread);

        bukkitService.scheduleSyncTaskFromOptionallyAsyncTask(task);

        if (isGlobalThread) {
            verify(task).run();
            verify(schedulingAdapter).isGlobalThread();
        } else {
            verify(schedulingAdapter).runOnGlobalThread(authMe, task);
            verifyNoInteractions(task);
        }
    }

    @ParameterizedTest(name = "entity owned by current thread: {0}")
    @ValueSource(booleans = {true, false})
    void shouldScheduleEntityTask(boolean isOwnedByCurrentThread) {
        Entity entity = mock(Player.class);
        Runnable task = mock(Runnable.class);
        given(schedulingAdapter.isOwnedByCurrentThread(entity)).willReturn(isOwnedByCurrentThread);

        bukkitService.scheduleSyncTaskFromOptionallyAsyncTask(entity, task);

        if (isOwnedByCurrentThread) {
            verify(task).run();
        } else {
            verify(schedulingAdapter).runOnEntityThread(authMe, entity, task);
            verifyNoInteractions(task);
        }
    }

    @Test
    void shouldCheckRegionOwnershipThroughAdapter() {
        // given
        Entity entity = mock(Player.class);
        Location location = new Location(null, 1.0, 2.0, 3.0);
        given(schedulingAdapter.isOwnedByCurrentThread(entity)).willReturn(true);
        given(schedulingAdapter.isOwnedByCurrentThread(location)).willReturn(false);

        // when / then
        assertThat(bukkitService.isOwnedByCurrentThread(entity), equalTo(true));
        assertThat(bukkitService.isOwnedByCurrentThread(location), equalTo(false));
    }

    @Test
    void shouldRunTaskInAsync() {
        // given
        Runnable task = mock(Runnable.class);
        BukkitService spy = Mockito.spy(bukkitService);
        doReturn(mock(CancellableTask.class)).when(spy).runTaskAsynchronously(task);

        // when
        spy.runTaskOptionallyAsync(task);

        // then
        verifyNoInteractions(task);
        verify(spy).runTaskAsynchronously(task);
    }

    @Test
    void shouldRunTaskDirectlyIfConfigured() {
        // given
        given(settings.getProperty(PluginSettings.USE_ASYNC_TASKS)).willReturn(false);
        bukkitService.reload(settings);
        BukkitService spy = Mockito.spy(bukkitService);
        Runnable task = mock(Runnable.class);

        // when
        spy.runTaskOptionallyAsync(task);

        // then
        verify(task).run();
        verify(spy, only()).runTaskOptionallyAsync(task);
    }

    @Test
    void shouldRunTaskAsynchronously() {
        // given
        Runnable task = () -> {/* noop */};
        CancellableTask cancellableTask = mock(CancellableTask.class);
        given(schedulingAdapter.runAsyncTask(authMe, task)).willReturn(cancellableTask);

        // when
        CancellableTask resultingTask = bukkitService.runTaskAsynchronously(task);

        // then
        assertThat(resultingTask, equalTo(cancellableTask));
        verify(schedulingAdapter, only()).runAsyncTask(authMe, task);
    }

    @Test
    void shouldRunTaskTimerAsynchronously() {
        // given
        Runnable task = mock(Runnable.class);
        long delay = 20L;
        long period = 4000L;
        CancellableTask cancellableTask = mock(CancellableTask.class);
        given(schedulingAdapter.runAsyncTaskTimer(authMe, task, delay, period)).willReturn(cancellableTask);

        // when
        CancellableTask resultingTask = bukkitService.runTaskTimerAsynchronously(task, delay, period);

        // then
        assertThat(resultingTask, equalTo(cancellableTask));
        verify(schedulingAdapter, only()).runAsyncTaskTimer(authMe, task, delay, period);
    }

    @Test
    void shouldBroadcastMessage() {
        // given
        String message = "Important message to all";
        given(server.broadcastMessage(message)).willReturn(24);

        // when
        int result = bukkitService.broadcastMessage(message);

        // then
        assertThat(result, equalTo(24));
        verify(server).broadcastMessage(message);
    }

    @ParameterizedTest(name = "async={0}")
    @ValueSource(booleans = {false, true})
    void shouldCreateAndEmitEvent(boolean async) {
        // given
        given(settings.getProperty(PluginSettings.USE_ASYNC_TASKS)).willReturn(async);
        bukkitService.reload(settings);
        Player player = mock(Player.class);
        given(server.getPluginManager()).willReturn(pluginManager);

        // when
        FailedLoginEvent event = bukkitService.createAndCallEvent(isAsync -> new FailedLoginEvent(player, isAsync));

        // then
        verify(pluginManager).callEvent(event);
        assertThat(event.isAsynchronous(), equalTo(async));
        assertThat(event.getPlayer(), equalTo(player));
    }
}
