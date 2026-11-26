package fr.xephi.authme.command.executable.authme;

import fr.xephi.authme.command.CommandUtils;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.service.BukkitService;
import fr.xephi.authme.service.FloodgateService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import javax.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Shows the offline, Mojang and Floodgate UUID of a player.
 */
public class UuidCommand extends AbstractPlayerAuthCommand {

    @Inject
    private BukkitService bukkitService;

    @Inject
    private FloodgateService floodgateService;

    @Override
    public void executeCommand(CommandSender sender, List<String> arguments) {
        String playerName = CommandUtils.getTargetPlayerName(sender, arguments);
        String offlineName = playerName.toLowerCase(Locale.ROOT);
        UUID offlineUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + offlineName)
            .getBytes(StandardCharsets.UTF_8));
        getCommonService().send(sender, MessageKey.ADMIN_UUID_OFFLINE, playerName, offlineUuid.toString());

        PlayerAuth auth = getDataSource().getAuth(playerName);
        UUID mojangUuid = auth == null ? null : auth.getPremiumUuid();
        if (mojangUuid == null) {
            getCommonService().send(sender, MessageKey.ADMIN_UUID_MOJANG_UNKNOWN, playerName);
        } else {
            getCommonService().send(sender, MessageKey.ADMIN_UUID_MOJANG, playerName, mojangUuid.toString());
        }

        Player player = bukkitService.getPlayerExact(playerName);
        UUID floodgateUuid = player == null ? null : player.getUniqueId();
        if (floodgateUuid == null || !floodgateService.isFloodgatePlayer(floodgateUuid)) {
            getCommonService().send(sender, MessageKey.ADMIN_UUID_FLOODGATE_UNKNOWN, playerName);
        } else {
            getCommonService().send(sender, MessageKey.ADMIN_UUID_FLOODGATE, playerName, floodgateUuid.toString());
        }
    }
}
