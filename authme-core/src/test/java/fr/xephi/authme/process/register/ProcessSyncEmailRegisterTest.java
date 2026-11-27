package fr.xephi.authme.process.register;

import fr.xephi.authme.TestHelper;
import fr.xephi.authme.data.limbo.LimboService;
import fr.xephi.authme.events.RegisterEvent;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.service.BukkitService;
import fr.xephi.authme.service.CommonService;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;

/**
 * Test for {@link ProcessSyncEmailRegister}.
 */
@ExtendWith(MockitoExtension.class)
class ProcessSyncEmailRegisterTest {

    @InjectMocks
    private ProcessSyncEmailRegister process;

    @Mock
    private BukkitService bukkitService;
    @Mock
    private CommonService service;
    @Mock
    private LimboService limboService;
    @Mock
    private Player player;

    @BeforeAll
    static void initLogger() {
        TestHelper.setupLogger();
    }

    @Test
    void shouldTellPlayerToActivateAccountAndKeepThemRestricted() {
        process.processEmailRegister(player);

        InOrder order = inOrder(service, limboService, bukkitService);
        order.verify(service).send(player, MessageKey.ACCOUNT_NOT_ACTIVATED);
        order.verify(limboService).replaceTasksAfterRegistration(player);
        order.verify(bukkitService).callEvent(any(RegisterEvent.class));
    }
}
