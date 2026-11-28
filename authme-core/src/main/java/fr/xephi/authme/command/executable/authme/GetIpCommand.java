package fr.xephi.authme.command.executable.authme;

import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.service.BukkitService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import javax.inject.Inject;
import java.net.InetSocketAddress;
import java.util.List;

public class GetIpCommand extends AbstractPlayerAuthCommand {

    @Inject
    private BukkitService bukkitService;

    @Override
    public void executeCommand(CommandSender sender, List<String> arguments) {
        String playerName = arguments.get(0);
        // Database access: keep it off the server thread
        bukkitService.runTaskOptionallyAsync(() -> {
            Player player = bukkitService.getPlayerExact(playerName);
            PlayerAuth auth = getDataSource().getAuth(playerName);

            final String displayName = player == null ? playerName : player.getName();
            // getAddress() and its resolved InetAddress may be null for players whose connection is not
            // fully established (or is already being torn down); avoid failing the whole command then.
            InetSocketAddress address = player == null ? null : player.getAddress();
            if (address != null && address.getAddress() != null) {
                sender.sendMessage("Current IP of " + displayName + " is "
                    + address.getAddress().getHostAddress() + ":" + address.getPort());
            }

            if (auth == null) {
                sender.sendMessage(displayName + " is not registered in the database");
            } else {
                sender.sendMessage("Database: last IP: " + auth.getLastIp() + ", registration IP: "
                    + auth.getRegistrationIp());
            }
        });
    }
}
