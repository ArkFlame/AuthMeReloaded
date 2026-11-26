package fr.xephi.authme.data.limbo;

import fr.xephi.authme.ConsoleLogger;
import fr.xephi.authme.initialization.Reloadable;
import fr.xephi.authme.output.ConsoleLoggerFactory;
import fr.xephi.authme.permission.PermissionsManager;
import fr.xephi.authme.settings.Settings;
import fr.xephi.authme.settings.properties.PluginSettings;
import fr.xephi.authme.util.StringUtils;
import org.bukkit.entity.Player;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.util.Collection;
import java.util.Collections;
import java.util.function.Consumer;

/**
 * Changes the permission group according to the auth status of the player and the configuration.
 * <p>
 * If this feature is enabled, the <i>primary permissions group</i> of a player is replaced until he has
 * logged in. Some permission plugins have a notion of a primary group; for other permission plugins the
 * first group is simply taken.
 * <p>
 * The groups that are used as replacement until the player logs in is configurable and depends on if
 * the player is registered or not. Note that some (all?) permission systems require the group to actually
 * exist for the replacement to take place. Furthermore, since some permission groups require that players
 * be in at least one group, this will mean that the player is not removed from his primary group.
 */
class AuthGroupHandler implements Reloadable {

    private final ConsoleLogger logger = ConsoleLoggerFactory.get(AuthGroupHandler.class);

    @Inject
    private PermissionsManager permissionsManager;

    @Inject
    private Settings settings;

    private UserGroup unregisteredGroup;
    private UserGroup registeredGroup;

    /**
     * Sets the group of a player by its authentication status.
     *
     * @param player the player
     * @param limbo the associated limbo player (nullable)
     * @param groupType the group type
     */
    void setGroup(Player player, LimboPlayer limbo, AuthGroupType groupType) {
        if (!useAuthGroups()) {
            return;
        }

        Collection<UserGroup> previousGroups = limbo == null ? Collections.emptyList() : limbo.getGroups();

        switch (groupType) {
            // Implementation note: some permission systems don't support players not being in any group,
            // so add the new group before removing the old ones
            case UNREGISTERED:
            case REGISTERED_UNAUTHENTICATED:
                boolean isUnregistered = groupType == AuthGroupType.UNREGISTERED;
                permissionsManager.addGroup(player, isUnregistered ? unregisteredGroup : registeredGroup);
                permissionsManager.removeGroup(player, isUnregistered ? registeredGroup : unregisteredGroup);
                processGroups(player, previousGroups, group -> permissionsManager.removeGroup(player, group), "remove");
                break;

            case LOGGED_IN:
                processGroups(player, previousGroups, group -> permissionsManager.addGroup(player, group), "restore");
                permissionsManager.removeGroup(player, unregisteredGroup);
                permissionsManager.removeGroup(player, registeredGroup);
                break;

            default:
                throw new IllegalStateException("Encountered unhandled auth group type '" + groupType + "'");
        }

        logger.debug(() -> player.getName() + " changed to "
            + groupType + ": has groups " + permissionsManager.getGroups(player));
    }

    /**
     * Applies the action to each valid group in the snapshot. A temporary group (e.g. a LuckPerms
     * group added with a time limit) or a group the permissions system refuses must not prevent
     * the remaining groups from being processed.
     *
     * @param player the player
     * @param groups the groups from the limbo player
     * @param action the action to apply to each group
     * @param actionName the action name used in error messages
     */
    private void processGroups(Player player, Collection<UserGroup> groups, Consumer<UserGroup> action,
                               String actionName) {
        for (UserGroup group : groups) {
            if (!isValidGroup(group)) {
                continue;
            }
            try {
                action.accept(group);
            } catch (RuntimeException e) {
                logger.logException("Unable to " + actionName + " permission group '"
                    + group.getGroupName() + "'", e);
            }
        }
    }

    private static boolean isValidGroup(UserGroup group) {
        return group != null && !StringUtils.isBlank(group.getGroupName());
    }

    /**
     * Returns whether the auth permissions group function should be used.
     *
     * @return true if should be used, false otherwise
     */
    private boolean useAuthGroups() {
        // Check whether the permissions check is enabled
        if (!settings.getProperty(PluginSettings.ENABLE_PERMISSION_CHECK)) {
            return false;
        }

        // Make sure group support is available
        if (!permissionsManager.hasGroupSupport()) {
            logger.warning("The current permissions system doesn't have group support, unable to set group!");
            return false;
        }
        return true;
    }

    @Override
    @PostConstruct
    public void reload() {
        unregisteredGroup = new UserGroup(settings.getProperty(PluginSettings.UNREGISTERED_GROUP));
        registeredGroup = new UserGroup(settings.getProperty(PluginSettings.REGISTERED_GROUP));
    }

}
