package fr.xephi.authme.listener;

import fr.xephi.authme.events.PreJoinDialogEvent;
import fr.xephi.authme.service.BukkitService;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import org.bukkit.Bukkit;

import java.net.InetSocketAddress;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Fires the cancellable {@link PreJoinDialogEvent} on the configuration-phase thread (always asynchronous).
 */
final class PreJoinDialogEventBridge {

    private static final Logger LOGGER = Logger.getLogger(PreJoinDialogEventBridge.class.getName());

    private PreJoinDialogEventBridge() {
    }

    /**
     * Cancelling the event suppresses only this dialog; a listener exception is logged and must not stop the join.
     *
     * @return {@code true} if AuthMe should show the dialog, {@code false} if a listener cancelled it
     */
    static boolean shouldShowDialog(PreJoinDialogEvent.DialogType dialogType, String playerName,
                                    PlayerConfigurationConnection connection, BukkitService bukkitService) {
        InetSocketAddress clientAddress = connection.getClientAddress();
        PreJoinDialogEvent event = new PreJoinDialogEvent(playerName, connection.getProfile().getId(),
            clientAddress == null ? null : clientAddress.getAddress(), dialogType,
            dialogType == PreJoinDialogEvent.DialogType.LOGIN, true);
        try {
            if (bukkitService != null) {
                bukkitService.callEvent(event);
            } else {
                Bukkit.getPluginManager().callEvent(event);
            }
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "A PreJoinDialogEvent listener threw an exception", e);
        }
        return !event.isCancelled();
    }
}
