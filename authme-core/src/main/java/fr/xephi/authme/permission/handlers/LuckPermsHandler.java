package fr.xephi.authme.permission.handlers;

import fr.xephi.authme.ConsoleLogger;
import fr.xephi.authme.data.limbo.UserGroup;
import fr.xephi.authme.output.ConsoleLoggerFactory;
import fr.xephi.authme.permission.PermissionNode;
import fr.xephi.authme.permission.PermissionsSystemType;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedPermissionData;
import net.luckperms.api.context.ContextSetFactory;
import net.luckperms.api.model.data.DataMutateResult;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.query.QueryMode;
import net.luckperms.api.query.QueryOptions;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * Handler for LuckPerms.
 *
 * @see <a href="https://www.spigotmc.org/resources/luckperms-an-advanced-permissions-system.28140/">LuckPerms SpigotMC page</a>
 * @see <a href="https://github.com/lucko/LuckPerms">LuckPerms on Github</a>
 */
public class LuckPermsHandler implements PermissionHandler {

    private final ConsoleLogger logger = ConsoleLoggerFactory.get(LuckPermsHandler.class);
    private LuckPerms luckPerms;

    public LuckPermsHandler() throws PermissionHandlerException {
        try {
            luckPerms = LuckPermsProvider.get();
        } catch (IllegalStateException e) {
            throw new PermissionHandlerException("Could not get api of LuckPerms", e);
        }
    }

    @Override
    public boolean addToGroup(OfflinePlayer player, UserGroup group) {
        Group newGroup = luckPerms.getGroupManager().getGroup(group.getGroupName());
        if (newGroup == null) {
            return false;
        }

        User user = getLoadedUser(player, "add group", false);
        if (user == null) {
            return false;
        }

        if (user.data().add(buildGroupNode(group)) == DataMutateResult.FAIL) {
            return false;
        }

        luckPerms.getUserManager().saveUser(user);
        return true;
    }

    @Override
    public boolean hasGroupSupport() {
        return true;
    }

    @Override
    public boolean hasPermissionOffline(String name, PermissionNode node) {
        User user = luckPerms.getUserManager().getUser(name);
        if (user == null) {
            logger.warning("LuckPermsHandler: tried to check permission for offline user "
                + name + " but it isn't loaded!");
            return false;
        }

        CachedPermissionData permissionData = user.getCachedData()
            .getPermissionData(QueryOptions.builder(QueryMode.CONTEXTUAL).build());
        return permissionData.checkPermission(node.getNode()).asBoolean();
    }

    @Override
    public boolean removeFromGroup(OfflinePlayer player, UserGroup group) {
        User user = getLoadedUser(player, "remove group", true);
        if (user == null) {
            return false;
        }

        boolean result = user.data().remove(InheritanceNode.builder(group.getGroupName()).build())
            != DataMutateResult.FAIL;

        luckPerms.getUserManager().saveUser(user);
        return result;
    }

    @Override
    public boolean setGroup(OfflinePlayer player, UserGroup group) {
        User user = getLoadedUser(player, "set group", true);
        if (user == null) {
            return false;
        }

        InheritanceNode groupNode = buildGroupNode(group);

        if (user.data().add(groupNode) == DataMutateResult.FAIL) {
            return false;
        }
        user.data().clear(node -> {
            if (!(node instanceof InheritanceNode)) {
                return false;
            }
            InheritanceNode inheritanceNode = (InheritanceNode) node;
            return !inheritanceNode.equals(groupNode);
        });

        luckPerms.getUserManager().saveUser(user);
        return true;
    }

    @Override
    public List<UserGroup> getGroups(OfflinePlayer player) {
        User user = getLoadedUser(player, "get groups", true);
        if (user == null) {
            return Collections.emptyList();
        }

        return user.getDistinctNodes().stream()
            .filter(node -> node instanceof InheritanceNode)
            .map(node -> (InheritanceNode) node)
            .map(node -> {
                Group group = luckPerms.getGroupManager().getGroup(node.getGroupName());
                if (group == null) {
                    return null;
                }
                return new LuckPermGroup(group, node.getContexts());
            })
            .filter(Objects::nonNull)
            .sorted((o1, o2) -> sortGroups(user, o1, o2))
            .map(g -> new UserGroup(g.getGroup().getName(), g.getContexts().toFlattenedMap()))
            .collect(Collectors.toList());
    }

    @Override
    public PermissionsSystemType getPermissionSystem() {
        return PermissionsSystemType.LUCK_PERMS;
    }

    @Override
    public void loadUserData(UUID uuid) throws PermissionLoadUserException {
        try {
            luckPerms.getUserManager().loadUser(uuid).get(5, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new PermissionLoadUserException("Unable to load the permission data of the user " + uuid, e);
        }
    }

    /** Returns the loaded LuckPerms user for the player, optionally logging if it is not loaded. */
    private User getLoadedUser(OfflinePlayer player, String action, boolean logWarning) {
        String playerName = player.getName();
        if (playerName == null) {
            return null;
        }
        User user = luckPerms.getUserManager().getUser(playerName);
        if (user == null && logWarning) {
            logger.warning("LuckPermsHandler: tried to " + action + " for offline user "
                + playerName + " but it isn't loaded!");
        }
        return user;
    }

    @NotNull
    private InheritanceNode buildGroupNode(UserGroup group) {
        ContextSetFactory contextSetFactory = luckPerms.getContextManager().getContextSetFactory();
        InheritanceNode.Builder builder = InheritanceNode.builder(group.getGroupName());
        if (group.getContextMap() != null) {
            group.getContextMap().forEach((k, v) -> builder.withContext((contextSetFactory.immutableOf(k, v))));
        }
        return builder.build();
    }

    private int sortGroups(User user, LuckPermGroup o1, LuckPermGroup o2) {
        Group group1 = o1.getGroup();
        Group group2 = o2.getGroup();
        String primaryGroup = user.getPrimaryGroup();
        if (group1.getName().equals(primaryGroup) || group2.getName().equals(primaryGroup)) {
            return group1.getName().equals(primaryGroup) ? 1 : -1;
        }

        int i = Integer.compare(group2.getWeight().orElse(0), group1.getWeight().orElse(0));
        return i != 0 ? i : group1.getName().compareToIgnoreCase(group2.getName());
    }
}
