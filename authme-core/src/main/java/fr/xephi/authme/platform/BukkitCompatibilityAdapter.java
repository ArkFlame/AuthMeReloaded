package fr.xephi.authme.platform;

import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.Optional;

/**
 * Platform-specific access to Bukkit behavior that differs between server versions.
 * Implementations resolve the differences reflectively so that core keeps compiling
 * against the oldest supported Bukkit API.
 */
public interface BukkitCompatibilityAdapter {

    /**
     * @param player the player whose locale is requested
     * @return the player's locale, or empty if the server version cannot provide one
     */
    Optional<String> getPlayerLocale(Player player);

    /**
     * @param world the world whose spawn protection is requested
     * @return the spawn-protection radius, or 0 if it cannot be determined
     */
    int getSpawnRadius(World world);

    /**
     * @param block the block to check
     * @return whether the block can be passed through
     */
    boolean isBlockPassable(Block block);
}
