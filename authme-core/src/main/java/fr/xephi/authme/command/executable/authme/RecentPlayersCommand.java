package fr.xephi.authme.command.executable.authme;

import com.google.common.annotations.VisibleForTesting;
import fr.xephi.authme.command.ExecutableCommand;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.service.BukkitService;
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

    @Inject
    private BukkitService bukkitService;

    @Override
    public void executeCommand(CommandSender sender, List<String> arguments) {
        // The recent-players query hits the database: keep it off the server thread
        bukkitService.runTaskOptionallyAsync(() -> {
            List<PlayerAuth> recentPlayers = dataSource.getRecentlyLoggedInPlayers();
            DateTimeFormatter formatter = DATE_FORMAT.withZone(getZoneId());
            sender.sendMessage(ChatColor.BLUE + "[AuthMe] Recently logged in players");
            for (PlayerAuth auth : recentPlayers) {
                Long lastLogin = auth.getLastLogin();
                sender.sendMessage("- " + auth.getRealName() + " (" + (lastLogin == null ? "never"
                    : formatter.format(ofEpochMilli(lastLogin))) + " with IP "
                    + auth.getLastIp() + ")");
            }
        });
    }

    @VisibleForTesting
    ZoneId getZoneId() {
        return ZoneId.systemDefault();
    }
}
