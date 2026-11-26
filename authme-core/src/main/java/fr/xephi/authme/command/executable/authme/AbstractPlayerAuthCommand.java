package fr.xephi.authme.command.executable.authme;

import fr.xephi.authme.command.ExecutableCommand;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.service.CommonService;
import org.bukkit.command.CommandSender;

import javax.inject.Inject;

/**
 * Common base for admin commands that read a player's data from the database.
 *
 * <p>Holds the shared data source and messaging services so that each command only
 * implements its own output. Looking up a player also reports {@link MessageKey#UNKNOWN_USER}
 * in one place instead of repeating that check in every command.
 */
public abstract class AbstractPlayerAuthCommand implements ExecutableCommand {

    @Inject
    private DataSource dataSource;

    @Inject
    private CommonService commonService;

    protected final DataSource getDataSource() {
        return dataSource;
    }

    protected final CommonService getCommonService() {
        return commonService;
    }

    /**
     * Looks up the given player and reports {@link MessageKey#UNKNOWN_USER} to the sender
     * if the player is not registered.
     *
     * @param sender the sender to report an unknown player to
     * @param playerName the name of the player to look up
     * @return the player's auth data, or null if the player is not registered
     */
    protected final PlayerAuth getAuthOrReportUnknown(CommandSender sender, String playerName) {
        PlayerAuth auth = dataSource.getAuth(playerName);
        if (auth == null) {
            commonService.send(sender, MessageKey.UNKNOWN_USER);
        }
        return auth;
    }
}
