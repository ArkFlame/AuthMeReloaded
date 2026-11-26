package fr.xephi.authme.listener;

import fr.xephi.authme.data.auth.PlayerCache;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.initialization.SettingsDependent;
import fr.xephi.authme.service.ValidationService;
import fr.xephi.authme.settings.Settings;
import fr.xephi.authme.settings.properties.RegistrationSettings;
import fr.xephi.authme.util.PlayerUtils;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityEvent;
import org.bukkit.event.player.PlayerEvent;

import javax.inject.Inject;

/**
 * Service class for the AuthMe listeners to determine whether an event should be canceled.
 */
public class ListenerService implements SettingsDependent {

    private final DataSource dataSource;
    private final PlayerCache playerCache;
    private final ValidationService validationService;

    private boolean isRegistrationForced;

    @Inject
    ListenerService(Settings settings, DataSource dataSource, PlayerCache playerCache,
                    ValidationService validationService) {
        this.dataSource = dataSource;
        this.playerCache = playerCache;
        this.validationService = validationService;
        reload(settings);
    }

    /**
     * Returns whether an event should be canceled (for unauthenticated, non-NPC players).
     *
     * @param event the event to process
     * @return true if the event should be canceled, false otherwise
     */
    public boolean shouldCancelEvent(EntityEvent event) {
        return shouldCancelEvent(event.getEntity());
    }

    /**
     * Returns, based on the entity associated with the event, whether or not the event should be canceled.
     *
     * @param entity the player entity to verify
     * @return true if the associated event should be canceled, false otherwise
     */
    public boolean shouldCancelEvent(Entity entity) {
        return entity instanceof Player && shouldCancelEvent((Player) entity);
    }

    /**
     * Returns whether an event should be canceled (for unauthenticated, non-NPC players).
     *
     * @param event the event to process
     * @return true if the event should be canceled, false otherwise
     */
    public boolean shouldCancelEvent(PlayerEvent event) {
        return shouldCancelEvent(event.getPlayer());
    }

    /**
     * Returns, based on the player associated with the event, whether or not the event should be canceled.
     *
     * @param player the player to verify
     * @return true if the associated event should be canceled, false otherwise
     */
    public boolean shouldCancelEvent(Player player) {
        if (player == null) {
            return false;
        }
        String name = player.getName();
        if (validationService.isUnrestricted(name)
            || playerCache.isAuthenticated(name)
            || (!isRegistrationForced && !dataSource.isAuthAvailable(name))) {
            return false;
        }
        return !PlayerUtils.isNpc(player);
    }

    @Override
    public void reload(Settings settings) {
        isRegistrationForced = settings.getProperty(RegistrationSettings.FORCE);
    }

}
