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

    /**
     * Applies the result of a TOTP key update in the database to the player's auth state.
     *
     * @param player the player whose two-factor authentication was updated
     * @param auth the authentication state of that player
     * @param databaseUpdated whether the database update succeeded
     * @param newTotpKey the key to set on the auth object, or null to remove it
     * @param successMessage message to send to the player on success
     */
    protected final void applyTotpKeyUpdate(Player player, PlayerAuth auth, boolean databaseUpdated,
                                            String newTotpKey, MessageKey successMessage) {
        if (databaseUpdated) {
            auth.setTotpKey(newTotpKey);
            getPlayerCache().updatePlayerIfPresent(auth);
            messages.send(player, successMessage);
        } else {
            messages.send(player, MessageKey.ERROR);
        }
    }
}
