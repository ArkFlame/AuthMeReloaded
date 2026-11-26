package fr.xephi.authme.process;

import fr.xephi.authme.permission.PlayerStatePermission;
import fr.xephi.authme.service.BukkitService;
import fr.xephi.authme.service.CommonService;
import fr.xephi.authme.service.bungeecord.BungeeSender;
import fr.xephi.authme.service.bungeecord.MessageType;
import fr.xephi.authme.util.InternetProtocolUtils;
import org.bukkit.entity.Player;

/** Small actions shared by the login, register, join and logout processes. */
public final class ProcessSharedActions {

    private ProcessSharedActions() {
    }

    /** Sends the BungeeCord login notification once the player is eligible for plugin messages. */
    public static void sendLoginMessageToProxy(BukkitService bukkitService, BungeeSender bungeeSender, Player player) {
        if (bungeeSender.isEnabled()) {
            // "You can't send plugin messages directly after a player joins" -- see
            // https://www.spigotmc.org/wiki/bukkit-bungee-plugin-messaging-channel/
            bukkitService.scheduleSyncDelayedTask(player, () ->
                bungeeSender.sendAuthMeBungeecordMessage(player, MessageType.LOGIN), 5L);
        }
    }

    /**
     * Checks whether a player is exempt from per-IP account limits: either it has the
     * multiple-accounts permission or its address is a loopback address.
     */
    public static boolean isExemptFromIpLimit(CommonService service, Player player, String ip) {
        return service.hasPermission(player, PlayerStatePermission.ALLOW_MULTIPLE_ACCOUNTS)
            || InternetProtocolUtils.isLoopbackAddress(ip);
    }

    /** Kicks the player on its owning thread with an already resolved message. */
    public static void kickPlayer(BukkitService bukkitService, Player player, String kickMessage) {
        bukkitService.scheduleSyncTaskFromOptionallyAsyncTask(player, () -> player.kickPlayer(kickMessage));
    }
}
