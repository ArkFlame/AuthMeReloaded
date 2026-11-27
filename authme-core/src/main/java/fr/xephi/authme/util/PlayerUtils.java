package fr.xephi.authme.util;

import org.bukkit.entity.Player;

/**
 * Player utilities.
 */
public final class PlayerUtils {

    // Utility class
    private PlayerUtils() {
    }

    /** Returns the IP of the given player. */
    public static String getPlayerIp(Player player) {
        return player.getAddress().getAddress().getHostAddress();
    }

    /** Returns whether the player is connected from the given IP; false if the player has no address (anymore). */
    public static boolean hasIp(Player player, String ip) {
        java.net.InetSocketAddress address = player.getAddress();
        return address != null && address.getAddress() != null
            && ip.equalsIgnoreCase(address.getAddress().getHostAddress());
    }

    /** Returns if the player is an NPC or not. */
    public static boolean isNpc(Player player) {
        return player.hasMetadata("NPC");
    }

}
