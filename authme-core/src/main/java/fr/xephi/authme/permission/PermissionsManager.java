package fr.xephi.authme.permission;

import com.google.common.annotations.VisibleForTesting;
import fr.xephi.authme.ConsoleLogger;
import fr.xephi.authme.data.limbo.UserGroup;
import fr.xephi.authme.initialization.Reloadable;
import fr.xephi.authme.output.ConsoleLoggerFactory;
import fr.xephi.authme.permission.handlers.LuckPermsHandler;
import fr.xephi.authme.permission.handlers.PermissionHandler;
import fr.xephi.authme.permission.handlers.PermissionHandlerException;
import fr.xephi.authme.permission.handlers.PermissionLoadUserException;
import fr.xephi.authme.permission.handlers.VaultHandler;
import fr.xephi.authme.settings.Settings;
import fr.xephi.authme.settings.properties.PluginSettings;
import fr.xephi.authme.util.StringUtils;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.permissions.ServerOperator;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * PermissionsManager.
 * <p>
 * A permissions manager, to manage and use various permissions systems.
 * This manager supports dynamic plugin hooking and various other features.
 * <p>
 * Written by Tim Visée.
 *
 * @author Tim Visée, http://timvisee.com
 * @version 0.3
 */
public class PermissionsManager implements Reloadable {

    private final ConsoleLogger logger = ConsoleLoggerFactory.get(PermissionsManager.class);
    private final Server server;
    private final PluginManager pluginManager;
    private final Settings settings;

    /**
     * The permission handler that is currently in use.
     * Null if no permission system is hooked.
     */
    private PermissionHandler handler = null;

    @Inject
    PermissionsManager(Server server, PluginManager pluginManager, Settings settings) {
        this.server = server;
        this.pluginManager = pluginManager;
        this.settings = settings;
    }

    /**
     * Check if the permissions manager is currently hooked into any of the supported permissions systems.
     *
     * @return False if there isn't any permissions system used.
     */
    public boolean isEnabled() {
        return handler != null;
    }

    /**
     * Setup and hook into the permissions systems.
     */
    @PostConstruct
    @VisibleForTesting
    void setup() {
        if (settings.getProperty(PluginSettings.FORCE_VAULT_HOOK)) {
            try {
                if (hookInto(PermissionsSystemType.VAULT)) {
                    return;
                }
            } catch (PermissionHandlerException e) {
                logger.logException("Failed to create Vault hook (forced):", e);
            }
        } else {
            for (PermissionsSystemType type : PermissionsSystemType.values()) {
                try {
                    if (hookInto(type)) {
                        return;
                    }
                } catch (Exception ex) {
                    logger.logException("Error while hooking into " + type.getDisplayName(), ex);
                }
            }
        }

        logger.info("No supported permissions system found! Permissions are disabled!");
    }

    /**
     * Creates the handler for the given type and stores it if the hook succeeds.
     *
     * @return true if a handler was created and stored
     * @throws PermissionHandlerException if the handler could not be initialized
     */
    private boolean hookInto(PermissionsSystemType type) throws PermissionHandlerException {
        Plugin plugin = pluginManager.getPlugin(type.getPluginName());
        if (plugin == null) {
            return false;
        }
        if (!plugin.isEnabled()) {
            logger.info("Not hooking into " + type.getDisplayName() + " because it's disabled!");
            return false;
        }

        PermissionHandler newHandler;
        switch (type) {
            case LUCK_PERMS:
                newHandler = new LuckPermsHandler();
                break;
            case VAULT:
                newHandler = new VaultHandler(server);
                break;
            default:
                throw new IllegalStateException("Unhandled permission type '" + type + "'");
        }
        this.handler = newHandler;
        logger.info("Hooked into " + type.getDisplayName() + "!");
        return true;
    }

    /**
     * Reload the permissions manager, and re-hook all permission plugins.
     */
    @Override
    public void reload() {
        handler = null;
        logger.info("Unhooked from Permissions!");
        setup();
    }

    /**
     * Method called when a plugin is being enabled.
     *
     * @param pluginName The name of the plugin being enabled.
     */
    public void onPluginEnable(String pluginName) {
        updateHooks(pluginName, "enabled, dynamically updating permissions hooks!");
    }

    /**
     * Method called when a plugin is being disabled.
     *
     * @param pluginName The name of the plugin being disabled.
     */
    public void onPluginDisable(String pluginName) {
        updateHooks(pluginName, "disabled, updating hooks!");
    }

    /** Re-hooks permissions systems when a supported plugin changes its state. */
    private void updateHooks(String pluginName, String action) {
        if (PermissionsSystemType.isPermissionSystem(pluginName)) {
            logger.info(pluginName + " plugin " + action);
            setup();
        }
    }

    /**
     * Return the permissions system that is hooked into.
     *
     * @return The permissions system, or null.
     */
    public PermissionsSystemType getPermissionSystem() {
        return isEnabled() ? handler.getPermissionSystem() : null;
    }

    /**
     * Check if the command sender has permission for the given permissions node. If no permissions system is used or
     * if the sender is not a player (e.g. console user), the player has to be OP in order to have the permission.
     *
     * @param sender         The command sender.
     * @param permissionNode The permissions node to verify.
     *
     * @return True if the sender has the permission, false otherwise.
     */
    public boolean hasPermission(CommandSender sender, PermissionNode permissionNode) {
        // Check if the permission node is null
        if (permissionNode == null) {
            return true;
        }

        // Return default if sender is not a player or no permission system is in use
        if (!(sender instanceof Player) || !isEnabled()) {
            return permissionNode.getDefaultPermission().evaluate(sender);
        }

        Player player = (Player) sender;
        return player.hasPermission(permissionNode.getNode());
    }

    /**
     * Check if a player has permission for the given permission node. This is for offline player checks.
     * If no permissions system is used, then the player will not have permission.
     *
     * @param player         The offline player
     * @param permissionNode The permission node to verify
     *
     * @return true if the player has permission, false otherwise
     */
    public boolean hasPermissionOffline(OfflinePlayer player, PermissionNode permissionNode) {
        return checkOfflinePermission(permissionNode, player, () -> player.getName());
    }

    /**
     * Check whether the offline player with the given name has permission for the given permission node.
     * This method is used as a last resort when nothing besides the name is known.
     *
     * @param name           The name of the player
     * @param permissionNode The permission node to verify
     *
     * @return true if the player has permission, false otherwise
     */
    public boolean hasPermissionOffline(String name, PermissionNode permissionNode) {
        return checkOfflinePermission(permissionNode, null, () -> name);
    }

    private boolean checkOfflinePermission(PermissionNode permissionNode, ServerOperator sender,
                                           Supplier<String> nameSupplier) {
        if (permissionNode == null) {
            return true;
        }
        if (!isEnabled()) {
            return permissionNode.getDefaultPermission().evaluate(sender);
        }
        return handler.hasPermissionOffline(nameSupplier.get(), permissionNode);
    }

    /**
     * Check whether the current permissions system has group support.
     * If no permissions system is hooked, false will be returned.
     *
     * @return True if the current permissions system supports groups, false otherwise.
     */
    public boolean hasGroupSupport() {
        return isEnabled() && handler.hasGroupSupport();
    }

    /**
     * Get the permission groups of a player, if available.
     *
     * @param player The player.
     *
     * @return Permission groups, or an empty collection if this feature is not supported.
     */
    public Collection<UserGroup> getGroups(OfflinePlayer player) {
        return isEnabled() ? handler.getGroups(player) : Collections.emptyList();
    }

    /**
     * Get the primary group of a player, if available.
     *
     * @param player The player.
     *
     * @return The name of the primary permission group. Or null.
     */
    public UserGroup getPrimaryGroup(OfflinePlayer player) {
        return isEnabled() ? handler.getPrimaryGroup(player) : null;
    }

    /**
     * Check whether the player is in the specified group.
     *
     * @param player    The player.
     * @param groupName The group name.
     *
     * @return True if the player is in the specified group, false otherwise.
     *         False is also returned if groups aren't supported by the used permissions system.
     */
    public boolean isInGroup(OfflinePlayer player, UserGroup groupName) {
        return isEnabled() && handler.isInGroup(player, groupName);
    }

    /**
     * Add the permission group of a player, if supported.
     *
     * @param player    The player
     * @param groupName The name of the group.
     *
     * @return True if succeed, false otherwise.
     *         False is also returned if this feature isn't supported for the current permissions system.
     */
    public boolean addGroup(OfflinePlayer player, UserGroup groupName) {
        if (!isEnabled() || StringUtils.isBlank(groupName.getGroupName())) {
            return false;
        }
        return handler.addToGroup(player, groupName);
    }

    /**
     * Add the permission groups of a player, if supported.
     *
     * @param player     The player
     * @param groupNames The name of the groups to add.
     *
     * @return True if at least one group was added, false otherwise.
     *         False is also returned if this feature isn't supported for the current permissions system.
     */
    public boolean addGroups(OfflinePlayer player, Collection<UserGroup> groupNames) {
        return changeGroups(player, groupNames, (p, g) -> handler.addToGroup(p, g));
    }

    /**
     * Remove the permission group of a player, if supported.
     *
     * @param player    The player
     * @param group The name of the group.
     *
     * @return True if succeed, false otherwise.
     *         False is also returned if this feature isn't supported for the current permissions system.
     */
    public boolean removeGroup(OfflinePlayer player, UserGroup group) {
        return isEnabled() && handler.removeFromGroup(player, group);
    }

    /**
     * Remove the permission groups of a player, if supported.
     *
     * @param player     The player
     * @param groupNames The name of the groups to remove.
     *
     * @return True if at least one group was removed, false otherwise.
     *         False is also returned if this feature isn't supported for the current permissions system.
     */
    public boolean removeGroups(OfflinePlayer player, Collection<UserGroup> groupNames) {
        return changeGroups(player, groupNames, (p, g) -> handler.removeFromGroup(p, g));
    }

    private boolean changeGroups(OfflinePlayer player, Collection<UserGroup> groupNames,
                                 BiFunction<OfflinePlayer, UserGroup, Boolean> action) {
        if (!isEnabled()) {
            return false;
        }

        boolean result = false;
        for (UserGroup group : groupNames) {
            if (!group.getGroupName().isEmpty()) {
                result |= action.apply(player, group);
            }
        }
        return result;
    }

    /**
     * Set the permission group of a player, if supported.
     * This clears the current groups of the player.
     *
     * @param player    The player
     * @param group The name of the group.
     *
     * @return True if succeed, false otherwise.
     *         False is also returned if this feature isn't supported for the current permissions system.
     */
    public boolean setGroup(OfflinePlayer player, UserGroup group) {
        return isEnabled() && handler.setGroup(player, group);
    }

    /**
     * Remove all groups of the specified player, if supported.
     * Systems like Essentials GroupManager don't allow all groups to be removed from a player, thus the user will stay
     * in its primary group. All the subgroups are removed just fine.
     *
     * @param player The player to remove all groups from.
     *
     * @return True if succeed, false otherwise.
     *         False will also be returned if this feature isn't supported for the used permissions system.
     */
    public boolean removeAllGroups(OfflinePlayer player) {
        return isEnabled() && removeGroups(player, getGroups(player));
    }

    /**
     * Loads the permission data of the given player.
     *
     * @param offlinePlayer the offline player.
     * @return true if the load was successful.
     */
    public boolean loadUserData(OfflinePlayer offlinePlayer) {
        try {
            loadUserData(offlinePlayer.getUniqueId());
        } catch (PermissionLoadUserException e) {
            logger.logException("Unable to load the permission data of user " + offlinePlayer.getName(), e);
            return false;
        }
        return true;
    }

    /**
     * Loads the permission data of the given player unique identifier.
     *
     * @param uuid the {@link UUID} of the player.
     * @throws PermissionLoadUserException if the action failed.
     */
    public void loadUserData(UUID uuid) throws PermissionLoadUserException {
        if (!isEnabled()) {
            return;
        }
        handler.loadUserData(uuid);
    }

}
