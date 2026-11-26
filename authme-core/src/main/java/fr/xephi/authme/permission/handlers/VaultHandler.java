package fr.xephi.authme.permission.handlers;

import com.google.common.annotations.VisibleForTesting;
import fr.xephi.authme.data.limbo.UserGroup;
import fr.xephi.authme.permission.PermissionNode;
import fr.xephi.authme.permission.PermissionsSystemType;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static java.util.stream.Collectors.toList;

/**
 * Handler for permissions via Vault.
 *
 * @see <a href="https://dev.bukkit.org/projects/vault">Vault Bukkit page</a>
 * @see <a href="https://github.com/milkbowl/Vault">Vault on Github</a>
 */
public class VaultHandler implements PermissionHandler {

    private final Permission vaultProvider;

    public VaultHandler(Server server) throws PermissionHandlerException {
        this.vaultProvider = getVaultPermission(server);
    }

    /**
     * Returns the Vault Permission interface.
     *
     * @param server the bukkit server instance
     * @return the vault permission instance
     * @throws PermissionHandlerException if the vault permission instance cannot be retrieved
     */
    @VisibleForTesting
    Permission getVaultPermission(Server server) throws PermissionHandlerException {
        RegisteredServiceProvider<Permission> permissionProvider = server
            .getServicesManager().getRegistration(Permission.class);
        if (permissionProvider == null) {
            throw new PermissionHandlerException("Could not load permissions provider service");
        }
        Permission vaultPerms = permissionProvider.getProvider();
        if (vaultPerms == null) {
            throw new PermissionHandlerException("Could not load Vault permissions provider");
        }
        return vaultPerms;
    }

    @Override
    public boolean addToGroup(OfflinePlayer player, UserGroup group) {
        return vaultProvider.playerAddGroup(null, player, group.getGroupName());
    }

    @Override
    public boolean hasGroupSupport() {
        return vaultProvider.hasGroupSupport();
    }

    @Override
    public boolean hasPermissionOffline(String name, PermissionNode node) {
        return vaultProvider.has((String) null, name, node.getNode());
    }

    @Override
    public boolean removeFromGroup(OfflinePlayer player, UserGroup group) {
        return vaultProvider.playerRemoveGroup(null, player, group.getGroupName());
    }

    @Override
    public boolean setGroup(OfflinePlayer player, UserGroup group) {
        for (UserGroup g : getGroups(player)) {
            removeFromGroup(player, g);
        }
        return addToGroup(player, group);
    }

    @Override
    public List<UserGroup> getGroups(OfflinePlayer player) {
        String[] groups = vaultProvider.getPlayerGroups(null, player);
        return groups == null ? Collections.emptyList() : Arrays.stream(groups).map(UserGroup::new).collect(toList());
    }

    @Override
    public UserGroup getPrimaryGroup(OfflinePlayer player) {
        String primaryGroup = vaultProvider.getPrimaryGroup(null, player);
        return primaryGroup == null ? null : new UserGroup(primaryGroup);
    }

    @Override
    public PermissionsSystemType getPermissionSystem() {
        return PermissionsSystemType.VAULT;
    }
}
