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
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;

/** Manages and uses the various supported permissions systems, including dynamic plugin hooking. */
public class PermissionsManager implements Reloadable {

    private final ConsoleLogger logger = ConsoleLoggerFactory.get(PermissionsManager.class);
    private final Server server;
    private final PluginManager pluginManager;
    private final Settings settings;

    /** The permission handler that is currently in use, null if no permissions system is hooked. */
    private volatile PermissionHandler handler = null;

    @Inject
    PermissionsManager(Server server, PluginManager pluginManager, Settings settings) {
        this.server = server;
        this.pluginManager = pluginManager;
        this.settings = settings;
    }

    /** @return true if the manager is hooked into a supported permissions system. */
    public boolean isEnabled() {
        return handler != null;
    }

    /** Setup and hook into the permissions systems. */
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
                    logger.logException("Error while hooking into " + type.getPluginName(), ex);
                }
            }
        }

        logger.info("No supported permissions system found! Permissions are disabled!");
    }

    /** Creates and stores the handler for the given type if its plugin is available and enabled. */
    private boolean hookInto(PermissionsSystemType type) throws PermissionHandlerException {
        Plugin plugin = pluginManager.getPlugin(type.getPluginName());
        if (plugin == null) {
            return false;
        }
        if (!plugin.isEnabled()) {
            logger.info("Not hooking into " + type.getPluginName() + " because it's disabled!");
            return false;
        }

        PermissionHandler newHandler;
        if (type == PermissionsSystemType.VAULT) {
            newHandler = new VaultHandler(server);
        } else if (type == PermissionsSystemType.LUCK_PERMS) {
            newHandler = new LuckPermsHandler();
        } else {
            throw new IllegalStateException("Unhandled permission type '" + type + "'");
        }
        this.handler = newHandler;
        logger.info("Hooked into " + type.getPluginName() + "!");
        return true;
    }

    /** Unhook from all permissions systems, then re-hook. */
    @Override
    public void reload() {
        handler = null;
        logger.info("Unhooked from Permissions!");
        setup();
    }

    /** Method called when a plugin is being enabled. */
    public void onPluginEnable(String pluginName) {
        updateHooks(pluginName, "enabled, dynamically updating permissions hooks!");
    }

    /** Method called when a plugin is being disabled. */
    public void onPluginDisable(String pluginName) {
        updateHooks(pluginName, "disabled, updating hooks!");
    }

    /** Re-hooks permissions systems when a supported plugin changes its state. */
    private void updateHooks(String pluginName, String action) {
        if (PermissionsSystemType.isPermissionSystem(pluginName)) {
            logger.info(pluginName + " plugin " + action);
            // Drop the old handler first: setup() won't clear it when no replacement is available.
            handler = null;
            setup();
        }
    }

    /** @return the permissions system that is hooked into, or null. */
    public PermissionsSystemType getPermissionSystem() {
        return isEnabled() ? handler.getPermissionSystem() : null;
    }

    /**
     * Check if the command sender has permission for the given permissions node. If no permissions system is used or
     * if the sender is not a player (e.g. console user), the player has to be OP in order to have the permission.
     *
     * @param sender         The command sender.
     * @param permissionNode The permissions node to verify.
     * @return True if the sender has the permission, false otherwise.
     */
    public boolean hasPermission(CommandSender sender, PermissionNode permissionNode) {
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

    /** Check a player's permission offline, falling back to the default permission when no system is hooked. */
    public boolean hasPermissionOffline(OfflinePlayer player, PermissionNode permissionNode) {
        return checkOfflinePermission(permissionNode, player, null);
    }

    /** Check an offline player's permission by name, used as a last resort when nothing else is known. */
    public boolean hasPermissionOffline(String name, PermissionNode permissionNode) {
        return checkOfflinePermission(permissionNode, null, name);
    }

    private boolean checkOfflinePermission(PermissionNode permissionNode, OfflinePlayer player, String name) {
        if (permissionNode == null) {
            return true;
        }
        // Resolve the name only after the handler is known to be available.
        return isEnabled()
            ? handler.hasPermissionOffline(player != null ? player.getName() : name, permissionNode)
            : permissionNode.getDefaultPermission().evaluate(player);
    }

    /** @return true if the current permissions system supports groups. */
    public boolean hasGroupSupport() {
        return isEnabled() && handler.hasGroupSupport();
    }

    /** @return the player's permission groups, or an empty collection if this feature is not supported. */
    public Collection<UserGroup> getGroups(OfflinePlayer player) {
        return isEnabled() ? handler.getGroups(player) : Collections.emptyList();
    }

    /** @return the name of the player's primary permission group, or null. */
    public UserGroup getPrimaryGroup(OfflinePlayer player) {
        return isEnabled() ? handler.getPrimaryGroup(player) : null;
    }

    /** Add the permission group of a player, if supported. */
    public boolean addGroup(OfflinePlayer player, UserGroup groupName) {
        return isEnabled() && groupName != null && !StringUtils.isBlank(groupName.getGroupName())
            && handler.addToGroup(player, groupName);
    }

    /** Add the permission groups of a player, if supported. */
    public boolean addGroups(OfflinePlayer player, Collection<UserGroup> groupNames) {
        return changeGroups(player, groupNames, true);
    }

    /** Remove the permission group of a player, if supported. */
    public boolean removeGroup(OfflinePlayer player, UserGroup group) {
        return isEnabled() && handler.removeFromGroup(player, group);
    }

    /** Remove the permission groups of a player, if supported. */
    public boolean removeGroups(OfflinePlayer player, Collection<UserGroup> groupNames) {
        return changeGroups(player, groupNames, false);
    }

    private boolean changeGroups(OfflinePlayer player, Collection<UserGroup> groupNames, boolean add) {
        PermissionHandler currentHandler = handler;
        if (currentHandler == null || groupNames == null) {
            return false;
        }

        boolean result = false;
        for (UserGroup group : groupNames) {
            if (group != null) {
                String groupName = group.getGroupName();
                if (groupName != null && !groupName.isEmpty()) {
                    result |= add ? currentHandler.addToGroup(player, group)
                                  : currentHandler.removeFromGroup(player, group);
                }
            }
        }
        return result;
    }

    /** Set the permission group of a player, clearing its current groups, if supported. */
    public boolean setGroup(OfflinePlayer player, UserGroup group) {
        return isEnabled() && handler.setGroup(player, group);
    }

    /**
     * Remove all groups of the specified player, if supported.
     * Systems like Essentials GroupManager don't allow all groups to be removed from a player, thus the user will stay
     * in its primary group. All the subgroups are removed just fine.
     */
    public boolean removeAllGroups(OfflinePlayer player) {
        return removeGroups(player, getGroups(player));
    }

    /** Load the permission data of the given offline player. */
    public boolean loadUserData(OfflinePlayer offlinePlayer) {
        if (!isEnabled()) {
            return true;
        }
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
        if (isEnabled()) {
            handler.loadUserData(uuid);
        }
    }

}
