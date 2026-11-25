package fr.xephi.authme.util;

import org.bukkit.entity.Player;

/**
 * Player utilities.
 */
public final class PlayerUtils {

    // Utility class
    private PlayerUtils() {
    }

    /**
     * Returns the IP of the given player.
     *
     * @param player The player to return the IP address for
     * @return The player's IP address
     */
    public static String getPlayerIp(Player player) {
        return player.getAddress().getAddress().getHostAddress();
    }

    /**
     * Returns whether the player's IP address is a loopback address. Such addresses must be ignored
     * for per-IP limits: with broken proxy forwarding all players may appear to use 127.0.0.1
     * and would otherwise share a single IP.
     *
     * @param player The player to check
     * @return True if the player's IP is a loopback address, false otherwise
     */
    public static boolean isPlayerIpLoopback(Player player) {
        return InternetProtocolUtils.isLoopbackAddress(getPlayerIp(player));
    }

    /**
     * Returns if the player is an NPC or not.
     *
     * @param player The player to check
     * @return True if the player is an NPC, false otherwise
     */
    public static boolean isNpc(Player player) {
        return player.hasMetadata("NPC");
    }

}
