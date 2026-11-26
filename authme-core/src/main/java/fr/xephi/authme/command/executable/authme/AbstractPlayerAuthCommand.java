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
     * Looks up the player and reports {@link MessageKey#UNKNOWN_USER} if not registered.
     *
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
