package fr.xephi.authme.task.purge;

import fr.xephi.authme.TestHelper;
import fr.xephi.authme.permission.PermissionsManager;
import fr.xephi.authme.permission.PlayerStatePermission;
import fr.xephi.authme.service.CancellableTask;
import org.bukkit.OfflinePlayer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.google.common.collect.Sets.newHashSet;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Regression test: a failure while executing a purge must not leave the service permanently
 * marked as purging, which would reject every later purge request.
 */
class PurgeTaskFailureTest {

    @BeforeAll
    static void initLogger() {
        TestHelper.setupLogger();
    }

    @Test
    void shouldResetPurgingStateWhenExecutePurgeFails() {
        PurgeService purgeService = mock(PurgeService.class);
        PermissionsManager permissionsManager = mock(PermissionsManager.class);
        CancellableTask taskHandle = mock(CancellableTask.class);

        OfflinePlayer player = mock(OfflinePlayer.class);
        given(player.getName()).willReturn("Alpha");
        given(permissionsManager.loadUserData(any(OfflinePlayer.class))).willReturn(true);
        given(permissionsManager.hasPermissionOffline(any(OfflinePlayer.class),
            eq(PlayerStatePermission.BYPASS_PURGE))).willReturn(false);
        doThrow(new RuntimeException("database unavailable"))
            .when(purgeService).executePurge(anyCollection(), anyCollection());

        PurgeTask task = new PurgeTask(purgeService, permissionsManager, null, newHashSet("alpha"),
            new OfflinePlayer[]{player});
        task.setTaskHandle(taskHandle);

        task.run();

        verify(purgeService).setPurging(false);
        verify(taskHandle).cancel();
    }
}
