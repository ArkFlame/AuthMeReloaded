package fr.xephi.authme.command.executable.authme;

import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.process.Management;
import fr.xephi.authme.service.BukkitService;
import org.bukkit.command.CommandSender;

import javax.inject.Inject;
import java.util.List;

/**
 * Admin command to unregister a player.
 */
public class UnregisterAdminCommand extends AbstractPlayerAuthCommand {

    @Inject
    private BukkitService bukkitService;

    @Inject
    private Management management;

    @Override
    public void executeCommand(final CommandSender sender, List<String> arguments) {
        String playerName = arguments.get(0);
        if (!getDataSource().isAuthAvailable(playerName)) {
            getCommonService().send(sender, MessageKey.UNKNOWN_USER);
            return;
        }
        management.performUnregisterByAdmin(sender, playerName, bukkitService.getPlayerExact(playerName));
    }
}
