package fr.xephi.authme.command.executable.authme;

import com.google.common.annotations.VisibleForTesting;
import fr.xephi.authme.command.ExecutableCommand;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.datasource.DataSource;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import javax.inject.Inject;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static java.time.Instant.ofEpochMilli;

/**
 * Command showing the most recent logged in players.
 */
public class RecentPlayersCommand implements ExecutableCommand {

    /** DateTime formatter, producing Strings such as "10:42 AM, 11 Jul". */
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("hh:mm a, dd MMM");

    @Inject
    private DataSource dataSource;

    @Override
    public void executeCommand(CommandSender sender, List<String> arguments) {
        List<PlayerAuth> recentPlayers = dataSource.getRecentlyLoggedInPlayers();

        sender.sendMessage(ChatColor.BLUE + "[AuthMe] Recently logged in players");
        for (PlayerAuth auth : recentPlayers) {
            Long lastLogin = auth.getLastLogin();
            String lastLoginText = lastLogin == null ? "never"
                : DATE_FORMAT.withZone(getZoneId()).format(ofEpochMilli(lastLogin));
            sender.sendMessage("- " + auth.getRealName() + " (" + lastLoginText + " with IP "
                + auth.getLastIp() + ")");
        }
    }

    @VisibleForTesting
    ZoneId getZoneId() {
        return ZoneId.systemDefault();
    }
}
