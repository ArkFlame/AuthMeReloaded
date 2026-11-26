package fr.xephi.authme.command.executable.authme;

import fr.xephi.authme.data.auth.PlayerAuth;
import org.bukkit.command.CommandSender;

import java.util.List;

/**
 * Removes the stored last position of a user or of all.
 */
public class PurgeLastPositionCommand extends AbstractPlayerAuthCommand {

    @Override
    public void executeCommand(CommandSender sender, List<String> arguments) {
        String playerName = CommandArguments.getTargetPlayerName(sender, arguments);

        if ("*".equals(playerName)) {
            for (PlayerAuth auth : getDataSource().getAllAuths()) {
                resetAndSaveLastPosition(auth);
            }
            sender.sendMessage("All players last position locations are now reset");
        } else {
            PlayerAuth auth = getAuthOrReportUnknown(sender, playerName);
            if (auth == null) {
                return;
            }

            resetAndSaveLastPosition(auth);
            sender.sendMessage(playerName + "'s last position location is now reset");
        }
    }

    private void resetAndSaveLastPosition(PlayerAuth auth) {
        auth.setQuitLocX(0d);
        auth.setQuitLocY(0d);
        auth.setQuitLocZ(0d);
        auth.setWorld("world");
        getDataSource().updateQuitLoc(auth);
        // TODO: send an update when a messaging service will be implemented (QUITLOC)
    }
}
