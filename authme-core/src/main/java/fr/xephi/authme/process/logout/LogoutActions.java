package fr.xephi.authme.process.logout;

import ch.jalu.configme.properties.Property;
import fr.xephi.authme.service.BukkitService;
import fr.xephi.authme.service.CommonService;
import fr.xephi.authme.settings.properties.RegistrationSettings;
import org.bukkit.entity.Player;

/**
 * Small actions shared by the logout and unregister processes.
 */
public final class LogoutActions {

    private LogoutActions() {
    }

    /**
     * Applies the configured blindness effect to the player, if the feature is enabled.
     * The timeout property is read only when the effect is actually applied.
     *
     * @param player the player to affect
     * @param service the service used to read the settings
     * @param bukkitService the service used to build the potion effect
     * @param timeoutProperty the timeout setting, in seconds
     */
    public static void applyBlindEffect(Player player, CommonService service, BukkitService bukkitService,
                                        Property<Integer> timeoutProperty) {
        if (service.getProperty(RegistrationSettings.APPLY_BLIND_EFFECT)) {
            int timeout = service.getProperty(timeoutProperty) * BukkitService.TICKS_PER_SECOND;
            player.addPotionEffect(bukkitService.createBlindnessEffect(timeout));
        }
    }
}
