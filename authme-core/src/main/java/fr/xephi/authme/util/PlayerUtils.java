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

    /** Returns if the player is an NPC or not. */
    public static boolean isNpc(Player player) {
        return player.hasMetadata("NPC");
    }

}
