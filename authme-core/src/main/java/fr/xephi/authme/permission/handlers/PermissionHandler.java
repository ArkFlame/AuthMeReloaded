package fr.xephi.authme.permission.handlers;

import fr.xephi.authme.data.limbo.UserGroup;
import fr.xephi.authme.permission.PermissionNode;
import fr.xephi.authme.permission.PermissionsSystemType;
import org.bukkit.OfflinePlayer;

import java.util.Collection;
import java.util.UUID;

public interface PermissionHandler {

    /**
     * Add the permission group of a player, if supported.
     *
     * @param player the player
     * @param group  the name of the group
     * @return true if successful, false otherwise or if unsupported
     */
    boolean addToGroup(OfflinePlayer player, UserGroup group);

    /**
     * Check whether the current permissions system has group support.
     * If no permissions system is hooked, false will be returned.
     *
     * @return True if the current permissions system supports groups, false otherwise.
     */
    boolean hasGroupSupport();

    /**
     * Check a permission for an offline player by name.
     *
     * @param name the player's name
     * @param node the permission node
     * @return true if the player has the permission
     */
    boolean hasPermissionOffline(String name, PermissionNode node);

    /**
     * Remove the permission group of a player, if supported.
     *
     * @param player the player
     * @param group  the name of the group
     * @return true if successful, false otherwise or if unsupported
     */
    boolean removeFromGroup(OfflinePlayer player, UserGroup group);

    /**
     * Set the permission group of a player, clearing its current groups, if supported.
     *
     * @param player the player
     * @param group  the name of the group
     * @return true if successful, false otherwise or if unsupported
     */
    boolean setGroup(OfflinePlayer player, UserGroup group);

    /**
     * Get the permission groups of a player, if available.
     *
     * @param player the player
     * @return the groups, or an empty list if unsupported
     */
    Collection<UserGroup> getGroups(OfflinePlayer player);

    /**
     * Get the primary group of a player, if available.
     *
     * @param player The player.
     * @return The name of the primary permission group. Or null.
     */
    default UserGroup getPrimaryGroup(OfflinePlayer player) {
        Collection<UserGroup> groups = getGroups(player);
        return groups == null || groups.isEmpty() ? null : groups.iterator().next();
    }

    /**
     * Get the permission system that is being used.
     *
     * @return The permission system.
     */
    PermissionsSystemType getPermissionSystem();

    default void loadUserData(UUID uuid) throws PermissionLoadUserException {
    }

}
