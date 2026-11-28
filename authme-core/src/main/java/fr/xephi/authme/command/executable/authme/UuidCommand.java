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
        // Database access: keep it off the server thread
        bukkitService.runTaskOptionallyAsync(() -> {
            UUID offlineUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + playerName.toLowerCase(Locale.ROOT))
                .getBytes(StandardCharsets.UTF_8));
            getCommonService().send(sender, MessageKey.ADMIN_UUID_OFFLINE, playerName, offlineUuid.toString());

            PlayerAuth auth = getDataSource().getAuth(playerName);
            sendUuid(sender, MessageKey.ADMIN_UUID_MOJANG, MessageKey.ADMIN_UUID_MOJANG_UNKNOWN, playerName,
                auth == null ? null : auth.getPremiumUuid());

            Player player = bukkitService.getPlayerExact(playerName);
            UUID playerUuid = player == null ? null : player.getUniqueId();
            sendUuid(sender, MessageKey.ADMIN_UUID_FLOODGATE, MessageKey.ADMIN_UUID_FLOODGATE_UNKNOWN, playerName,
                playerUuid != null && floodgateService.isFloodgatePlayer(playerUuid) ? playerUuid : null);
        });
    }

    private void sendUuid(CommandSender sender, MessageKey knownKey, MessageKey unknownKey,
                          String playerName, UUID uuid) {
        if (uuid == null) {
            getCommonService().send(sender, unknownKey, playerName);
        } else {
            getCommonService().send(sender, knownKey, playerName, uuid.toString());
        }
    }
}
