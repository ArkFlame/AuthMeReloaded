package fr.xephi.authme.command.executable.authme;

import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.service.BukkitService;
import fr.xephi.authme.util.PlayerUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import javax.inject.Inject;
import java.util.List;

public class GetIpCommand extends AbstractPlayerAuthCommand {

    @Inject
    private BukkitService bukkitService;

    @Override
    public void executeCommand(CommandSender sender, List<String> arguments) {
        String playerName = arguments.get(0);
        Player player = bukkitService.getPlayerExact(playerName);
        PlayerAuth auth = getDataSource().getAuth(playerName);

        final String displayName = player == null ? playerName : player.getName();
        if (player != null) {
            sender.sendMessage("Current IP of " + displayName + " is " + PlayerUtils.getPlayerIp(player)
                + ":" + player.getAddress().getPort());
        }

        if (auth == null) {
            sender.sendMessage(displayName + " is not registered in the database");
        } else {
            sender.sendMessage("Database: last IP: " + auth.getLastIp() + ", registration IP: "
                + auth.getRegistrationIp());
        }
    }
}
