package fr.xephi.authme.process;

import fr.xephi.authme.permission.PlayerStatePermission;
import fr.xephi.authme.service.BukkitService;
import fr.xephi.authme.service.CommonService;
import fr.xephi.authme.service.bungeecord.BungeeSender;
import fr.xephi.authme.service.bungeecord.MessageType;
import fr.xephi.authme.util.InternetProtocolUtils;
import org.bukkit.entity.Player;

/**
 * Small actions shared by the login, register, join and logout processes.
 */
public final class ProcessSharedActions {

    private ProcessSharedActions() {
    }

    /**
     * Sends the BungeeCord login notification once the player is eligible for plugin messages.
     * Both the regular login and the join flow need this delayed notification.
     *
     * @param bukkitService the bukkit service used to schedule the message
     * @param bungeeSender the sender which skips the message when no proxy is in use
     * @param player the player who logged in
     */
    public static void sendLoginMessageToProxy(BukkitService bukkitService, BungeeSender bungeeSender, Player player) {
        if (bungeeSender.isEnabled()) {
            // As described at https://www.spigotmc.org/wiki/bukkit-bungee-plugin-messaging-channel/
            // "Keep in mind that you can't send plugin messages directly after a player joins."
            bukkitService.scheduleSyncDelayedTask(player, () ->
                bungeeSender.sendAuthMeBungeecordMessage(player, MessageType.LOGIN), 5L);
        }
    }

    /**
     * Checks whether a player is exempt from per-IP account limits. This is the case for players
     * with the multiple-accounts permission and for loopback addresses (proxy forwarding may
     * report the same local address for all players).
     *
     * @param service the common service used for the permission check
     * @param player the player to check
     * @param ip the player's IP address, resolved once by the caller
     * @return true if the IP limit must not be applied to this player
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
