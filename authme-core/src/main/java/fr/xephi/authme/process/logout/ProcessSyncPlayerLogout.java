package fr.xephi.authme.process.logout;

import fr.xephi.authme.ConsoleLogger;
import fr.xephi.authme.data.limbo.LimboService;
import fr.xephi.authme.events.LogoutEvent;
import fr.xephi.authme.listener.packetevents.PacketEventsService;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.output.ConsoleLoggerFactory;
import fr.xephi.authme.process.SynchronousProcess;
import fr.xephi.authme.service.BukkitService;
import fr.xephi.authme.service.CommonService;
import fr.xephi.authme.service.TeleportationService;
import fr.xephi.authme.settings.commandconfig.CommandManager;
import fr.xephi.authme.settings.properties.RestrictionSettings;
import org.bukkit.entity.Player;

import javax.inject.Inject;

public class ProcessSyncPlayerLogout implements SynchronousProcess {

    private static final ConsoleLogger LOGGER = ConsoleLoggerFactory.get(ProcessSyncPlayerLogout.class);

    @Inject
    private CommonService service;

    @Inject
    private BukkitService bukkitService;

    @Inject
    private PacketEventsService packetEventsService;

    @Inject
    private LimboService limboService;

    @Inject
    private TeleportationService teleportationService;

    @Inject
    private CommandManager commandManager;

    ProcessSyncPlayerLogout() {
    }

    /**
     * Processes a player which has been logged out.
     *
     * @param player the player logging out
     */
    public void processSyncLogout(Player player) {
        packetEventsService.sendBlankInventoryPacket(player);

        player.leaveVehicle();
        teleportationService.teleportOnJoin(player);

        LogoutActions.applyBlindEffect(player, service, bukkitService, RestrictionSettings.LOGIN_TIMEOUT);
        limboService.createLimboPlayer(player, true);

        commandManager.runCommandsOnLogout(player);

        // Player is now logged out. Time to fire the event!
        bukkitService.callEvent(new LogoutEvent(player));

        service.send(player, MessageKey.LOGOUT_SUCCESS);
        LOGGER.info(player.getName() + " logged out");
    }

}
