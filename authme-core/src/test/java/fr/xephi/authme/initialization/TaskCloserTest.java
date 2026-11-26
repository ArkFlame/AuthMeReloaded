package fr.xephi.authme.initialization;

import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import fr.xephi.authme.AuthMe;
import fr.xephi.authme.datasource.DataSource;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginLogger;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitWorker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockMakers;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Test for {@link TaskCloser}.
 */
@ExtendWith(MockitoExtension.class)
class TaskCloserTest {

    private static final int[] ACTIVE_WORKERS_ID = {2, 5};

    private TaskCloser taskCloser;
    @Mock(mockMaker = MockMakers.INLINE)
    private AuthMe authMe;
    @Mock
    private PluginLogger logger;
    @Mock
    private BukkitScheduler bukkitScheduler;
    @Mock
    private DataSource dataSource;
    @Captor
    private ArgumentCaptor<Integer> taskIds;

    @BeforeEach
    void initAuthMe() {
        Server server = mock(Server.class);
        given(server.getScheduler()).willReturn(bukkitScheduler);
        given(authMe.getServer()).willReturn(server);
        given(authMe.getLogger()).willReturn(logger);
        mockActiveWorkers();
        taskCloser = spy(new TaskCloser(authMe, dataSource));
    }

    @ParameterizedTest
    @CsvSource({"false, 2, 3", "true, 60, 61"})
    void shouldPollUntilTasksAreDone(boolean neverEnding, int expectedSleeps, int expectedChecks)
            throws InterruptedException {
        doNothing().when(taskCloser).sleep(); // avoid sleeping in tests
        given(bukkitScheduler.isCurrentlyRunning(ACTIVE_WORKERS_ID[0])).willReturn(neverEnding);
        // first and second check of the second worker
        given(bukkitScheduler.isCurrentlyRunning(ACTIVE_WORKERS_ID[1])).willReturn(!neverEnding, false);

        taskCloser.run();

        verify(bukkitScheduler, times(3)).isQueued(anyInt());
        verify(bukkitScheduler, times(expectedChecks)).isCurrentlyRunning(taskIds.capture());
        if (!neverEnding) {
            assertThat(taskIds.getAllValues(),
                contains(ACTIVE_WORKERS_ID[0], ACTIVE_WORKERS_ID[1], ACTIVE_WORKERS_ID[1]));
        }
        verify(taskCloser, times(expectedSleeps)).sleep();
        verify(dataSource).closeConnection();
    }

    @Test
    void shouldCloseDataSourceWhenSchedulerCheckFails() throws InterruptedException {
        doNothing().when(taskCloser).sleep(); // avoid sleeping in tests
        given(bukkitScheduler.isCurrentlyRunning(ACTIVE_WORKERS_ID[0]))
            .willThrow(new IllegalStateException("scheduler unavailable"));

        assertThrows(IllegalStateException.class, taskCloser::run);

        verify(taskCloser).sleep();
        verify(dataSource).closeConnection();
    }

    @Test
    void shouldStopForInterruptedThread() throws InterruptedException, ExecutionException {
        // Note ljacqu 20160827: This test must be run in its own thread because we throw an InterruptedException.
        // Somehow the java.nio.Files API used in tests that are run subsequently don't like this and fail otherwise.
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            try {
                shouldStopForInterruptedThread0();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }).get();
    }

    /** Test implementation for {@link #shouldStopForInterruptedThread()}. */
    private void shouldStopForInterruptedThread0() throws InterruptedException {
        taskCloser = spy(new TaskCloser(authMe, null));
        // First two times do nothing, third time throw exception when we sleep
        doNothing().doNothing().doThrow(InterruptedException.class).when(taskCloser).sleep();
        given(bukkitScheduler.isCurrentlyRunning(anyInt())).willReturn(true);

        taskCloser.run();

        verify(bukkitScheduler, times(4)).isCurrentlyRunning(anyInt());
        verify(taskCloser, times(3)).sleep();
    }

    private void mockActiveWorkers() {
        Plugin otherOwner = mock(Plugin.class);
        List<BukkitWorker> tasks = Arrays.asList(
            mockBukkitWorker(authMe, ACTIVE_WORKERS_ID[0], false),
            mockBukkitWorker(otherOwner, 3, false),
            mockBukkitWorker(authMe, ACTIVE_WORKERS_ID[1], false),
            mockBukkitWorker(authMe, 7, true));
        given(bukkitScheduler.getActiveWorkers()).willReturn(tasks);
    }

    private BukkitWorker mockBukkitWorker(Plugin owner, int taskId, boolean isQueued) {
        BukkitWorker worker = mock(BukkitWorker.class);
        given(worker.getOwner()).willReturn(owner);
        if (owner == authMe) {
            given(worker.getTaskId()).willReturn(taskId);
            given(bukkitScheduler.isQueued(taskId)).willReturn(isQueued);
        }
        return worker;
    }
}


