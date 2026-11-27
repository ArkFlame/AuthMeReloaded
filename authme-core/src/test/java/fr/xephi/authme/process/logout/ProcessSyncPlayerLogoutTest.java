package fr.xephi.authme.process.logout;

import fr.xephi.authme.TestHelper;
import fr.xephi.authme.events.LogoutEvent;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.data.limbo.LimboService;
import fr.xephi.authme.service.BukkitService;
import fr.xephi.authme.service.CommonService;
import fr.xephi.authme.service.TeleportationService;
import fr.xephi.authme.settings.commandconfig.CommandManager;
import fr.xephi.authme.settings.properties.RegistrationSettings;
import fr.xephi.authme.listener.packetevents.PacketEventsService;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;

/**
 * Test for {@link ProcessSyncPlayerLogout}.
 */
@ExtendWith(MockitoExtension.class)
class ProcessSyncPlayerLogoutTest {

    @InjectMocks
    private ProcessSyncPlayerLogout process;

    @Mock
    private CommonService service;
    @Mock
    private BukkitService bukkitService;
    @Mock
    private PacketEventsService packetEventsService;
    @Mock
    private LimboService limboService;
    @Mock
    private TeleportationService teleportationService;
    @Mock
    private CommandManager commandManager;
    @Mock
    private Player player;

    @BeforeAll
    static void initLogger() {
        TestHelper.setupLogger();
    }

    @Test
    void shouldRestrictThePlayerBeforeAnnouncingTheLogout() {
        given(service.getProperty(RegistrationSettings.APPLY_BLIND_EFFECT)).willReturn(false);
        given(player.getName()).willReturn("bobby");

        process.processSyncLogout(player);

        InOrder order = inOrder(packetEventsService, player, teleportationService, limboService,
            commandManager, bukkitService, service);
        order.verify(packetEventsService).sendBlankInventoryPacket(player);
        order.verify(player).leaveVehicle();
        order.verify(teleportationService).teleportOnJoin(player);
        order.verify(limboService).createLimboPlayer(player, true);
        order.verify(commandManager).runCommandsOnLogout(player);
        order.verify(bukkitService).callEvent(any(LogoutEvent.class));
        order.verify(service).send(player, MessageKey.LOGOUT_SUCCESS);
    }
}
