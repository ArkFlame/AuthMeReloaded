package fr.xephi.authme.command.executable.authme;

import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.message.Messages;
import fr.xephi.authme.service.BukkitService;
import org.bukkit.command.CommandSender;

import javax.inject.Inject;
import java.util.List;

/**
 * Common base for admin commands that inspect or change a player's two-factor authentication.
 */
public abstract class AbstractTotpAdminCommand extends AbstractPlayerAuthCommand {

    @Inject
    private Messages messages;

    @Inject
    private BukkitService bukkitService;

    @Override
    public final void executeCommand(CommandSender sender, List<String> arguments) {
        String playerName = arguments.get(0);
        // The lookup and any update hit the database: keep them off the server thread
        bukkitService.runTaskOptionallyAsync(() -> {
            PlayerAuth auth = getDataSource().getAuth(playerName);
            if (auth == null) {
                messages.send(sender, MessageKey.UNKNOWN_USER);
                return;
            }
            handlePlayer(sender, playerName, auth);
        });
    }

    protected final Messages getMessages() {
        return messages;
    }

    protected abstract void handlePlayer(CommandSender sender, String playerName, PlayerAuth auth);
}
