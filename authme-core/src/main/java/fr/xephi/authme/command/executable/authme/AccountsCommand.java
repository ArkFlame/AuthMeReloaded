package fr.xephi.authme.command.executable.authme;

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
        // TODO #1366: last IP vs. registration IP?
        if (arguments.isEmpty() && !(sender instanceof Player)) {
            // Console has no own account; fixes HaHaWTH/AuthMeReReloaded#244
            sender.sendMessage("Usage: /authme accounts <player|ip>");
            return;
        }
        final String playerName = CommandArguments.getTargetPlayerName(sender, arguments);

        // Assumption: a player name cannot contain '.'
        if (playerName.contains(".")) {
            bukkitService.runTaskAsynchronously(() ->
                outputAccountsByIp(sender, playerName, playerName, MessageKey.ACCOUNTS_IP_NOT_FOUND));
        } else {
            bukkitService.runTaskAsynchronously(() -> {
                PlayerAuth auth = getAuthOrReportUnknown(sender, playerName.toLowerCase(Locale.ROOT));
                if (auth == null) {
                    return;
                } else if (auth.getLastIp() == null) {
                    getCommonService().send(sender, MessageKey.ACCOUNTS_NO_LAST_IP);
                    return;
                }
                outputAccountsByIp(sender, playerName, auth.getLastIp(), MessageKey.UNKNOWN_USER);
            });
        }
    }

    private void outputAccountsByIp(CommandSender sender, String playerName, String ip, MessageKey notFoundKey) {
        List<String> accountList = getDataSource().getAllAuthsByIp(ip);
        if (accountList.isEmpty()) {
            getCommonService().send(sender, notFoundKey);
        } else if (accountList.size() == 1) {
            getCommonService().send(sender, MessageKey.ACCOUNTS_SINGLE, playerName);
        } else {
            outputAccountsList(sender, playerName, accountList);
        }
    }

    private void outputAccountsList(CommandSender sender, String playerName, List<String> accountList) {
        if (playerName.equalsIgnoreCase(sender.getName())) {
            getCommonService().send(sender, MessageKey.ACCOUNTS_OWNED_SELF, Integer.toString(accountList.size()));
        } else {
            getCommonService().send(sender, MessageKey.ACCOUNTS_OWNED_OTHER, playerName, Integer.toString(accountList.size()));
        }
        sender.sendMessage(String.join(", ", accountList));
    }
}
