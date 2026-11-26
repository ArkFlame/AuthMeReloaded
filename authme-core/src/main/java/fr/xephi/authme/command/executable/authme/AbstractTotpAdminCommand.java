package fr.xephi.authme.command.executable.authme;

import fr.xephi.authme.command.ExecutableCommand;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.message.Messages;
import org.bukkit.command.CommandSender;

import javax.inject.Inject;
import java.util.List;

/**
 * Common base for admin commands that inspect or change a player's two-factor authentication.
 */
public abstract class AbstractTotpAdminCommand implements ExecutableCommand {

    @Inject
    private DataSource dataSource;

    @Inject
    private Messages messages;

    @Override
    public final void executeCommand(CommandSender sender, List<String> arguments) {
        String playerName = arguments.get(0);
        PlayerAuth auth = dataSource.getAuth(playerName);
        if (auth == null) {
            messages.send(sender, MessageKey.UNKNOWN_USER);
            return;
        }
        handlePlayer(sender, playerName, auth);
    }

    protected final DataSource getDataSource() {
        return dataSource;
    }

    protected final Messages getMessages() {
        return messages;
    }

    protected abstract void handlePlayer(CommandSender sender, String playerName, PlayerAuth auth);
}
