package fr.xephi.authme.command.executable.authme;

import fr.xephi.authme.command.CommandUtils;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.service.BukkitService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import javax.inject.Inject;
import java.util.List;
import java.util.Locale;

/**
 * Shows all accounts registered by the same IP address for the given player name or IP address.
 */
public class AccountsCommand extends AbstractPlayerAuthCommand {

    @Inject
    private BukkitService bukkitService;

    @Override
    public void executeCommand(final CommandSender sender, List<String> arguments) {
        if (arguments.isEmpty() && !(sender instanceof Player)) {
            // Console has no own account; fixes HaHaWTH/AuthMeReReloaded#244
            sender.sendMessage("Usage: /authme accounts <player|ip>");
            return;
        }
        final String playerName = CommandUtils.getTargetPlayerName(sender, arguments);

        bukkitService.runTaskAsynchronously(() -> {
            // Assumption: a player name cannot contain '.'
            if (playerName.contains(".")) {
                outputAccountsByIp(sender, playerName, playerName, MessageKey.ACCOUNTS_IP_NOT_FOUND);
                return;
            }
            PlayerAuth auth = getAuthOrReportUnknown(sender, playerName.toLowerCase(Locale.ROOT));
            if (auth == null) {
                return;
            }
            String lastIp = auth.getLastIp();
            if (lastIp == null) {
                getCommonService().send(sender, MessageKey.ACCOUNTS_NO_LAST_IP);
                return;
            }
            outputAccountsByIp(sender, playerName, lastIp, MessageKey.UNKNOWN_USER);
        });
    }

    private void outputAccountsByIp(CommandSender sender, String playerName, String ip, MessageKey notFoundKey) {
        List<String> accountList = getDataSource().getAllAuthsByIp(ip);
        if (accountList.isEmpty()) {
            getCommonService().send(sender, notFoundKey);
        } else if (accountList.size() == 1) {
            getCommonService().send(sender, MessageKey.ACCOUNTS_SINGLE, playerName);
        } else {
            if (playerName.equalsIgnoreCase(sender.getName())) {
                getCommonService().send(sender, MessageKey.ACCOUNTS_OWNED_SELF, Integer.toString(accountList.size()));
            } else {
                getCommonService().send(sender, MessageKey.ACCOUNTS_OWNED_OTHER, playerName,
                    Integer.toString(accountList.size()));
            }
            sender.sendMessage(String.join(", ", accountList));
        }
    }
}
