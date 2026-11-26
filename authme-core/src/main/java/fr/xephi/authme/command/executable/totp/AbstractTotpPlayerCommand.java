package fr.xephi.authme.command.executable.totp;

import fr.xephi.authme.command.PlayerCommand;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.data.auth.PlayerCache;
import fr.xephi.authme.message.MessageKey;
import org.bukkit.entity.Player;

import javax.inject.Inject;
import java.util.List;

/**
 * Common base for player commands that act on the executing player's two-factor authentication.
 * Resolves the player's authentication state once and rejects players who are not logged in.
 */
public abstract class AbstractTotpPlayerCommand extends PlayerCommand {

    @Inject
    private PlayerCache playerCache;

    @Override
    protected final void runCommand(Player player, List<String> arguments) {
        PlayerAuth auth = playerCache.getAuth(player.getName());
        if (auth == null) {
            messages.send(player, MessageKey.NOT_LOGGED_IN);
        } else {
            handlePlayer(player, auth, arguments);
        }
    }

    /**
     * Acts on the given player's two-factor authentication state.
     *
     * @param player the player who executed the command
     * @param auth the authentication state of that player
     * @param arguments the arguments supplied with the command
     */
    protected abstract void handlePlayer(Player player, PlayerAuth auth, List<String> arguments);

    protected final PlayerCache getPlayerCache() {
        return playerCache;
    }
}
