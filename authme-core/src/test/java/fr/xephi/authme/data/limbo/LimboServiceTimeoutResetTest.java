package fr.xephi.authme.data.limbo;

import fr.xephi.authme.ReflectionTestUtils;
import fr.xephi.authme.TestHelper;
import fr.xephi.authme.service.CancellableTask;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Verifies that a register timeout left on the LimboPlayer cannot outlive the registration.
 */
@ExtendWith(MockitoExtension.class)
class LimboServiceTimeoutResetTest {

    @InjectMocks
    private LimboService limboService;

    @Mock
    private LimboPlayerTaskManager taskManager;

    @Mock
    private AuthGroupHandler authGroupHandler;

    @BeforeAll
    static void initLogger() {
        TestHelper.setupLogger();
    }

    @Test
    void shouldCancelLeftoverRegisterTimeoutOnRegistration() {
        // given - a register timeout task is still pending and no login timeout is configured
        Player player = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        given(player.getUniqueId()).willReturn(uuid);

        LimboPlayer limbo = new LimboPlayer(null, false, Collections.emptyList(), false, 0.2f, 0.1f);
        CancellableTask registerTimeoutTask = mock(CancellableTask.class);
        limbo.setTimeoutTask(registerTimeoutTask);
        getLimboMap().put(uuid, limbo);

        // when
        limboService.replaceTasksAfterRegistration(player);

        // then
        verify(registerTimeoutTask).cancel();
        verify(taskManager).registerTimeoutTask(player, limbo, LimboMessageType.LOG_IN);
    }

    private Map<UUID, LimboPlayer> getLimboMap() {
        return ReflectionTestUtils.getFieldValue(LimboService.class, limboService, "entries");
    }
}
