package fr.xephi.authme.service;

import fr.xephi.authme.initialization.SettingsDependent;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.message.Messages;
import fr.xephi.authme.permission.AdminPermission;
import fr.xephi.authme.permission.PermissionsManager;
import fr.xephi.authme.settings.Settings;
import fr.xephi.authme.settings.properties.ProtectionSettings;
import fr.xephi.authme.util.AtomicIntervalCounter;
import org.bukkit.entity.Player;

import javax.inject.Inject;

import static fr.xephi.authme.service.BukkitService.TICKS_PER_MINUTE;
import static fr.xephi.authme.service.BukkitService.TICKS_PER_SECOND;

/**
 * The AntiBot Service Management class.
 */
public class AntiBotService implements SettingsDependent {

    // Instances
    private final Messages messages;
    private final PermissionsManager permissionsManager;
    private final BukkitService bukkitService;
    // Settings
    private volatile int duration;
    // Service status - read on the async pre-login thread, written on the region/command thread
    private volatile AntiBotStatus antiBotStatus;
    private boolean startup = true;
    private volatile CancellableTask disableTask;
    private volatile AtomicIntervalCounter flaggedCounter;

    @Inject
    AntiBotService(Settings settings, Messages messages, PermissionsManager permissionsManager,
                   BukkitService bukkitService) {
        this.messages = messages;
        this.permissionsManager = permissionsManager;
        this.bukkitService = bukkitService;
        antiBotStatus = AntiBotStatus.DISABLED;
        reload(settings);
    }

    @Override
    public void reload(Settings settings) {
        duration = settings.getProperty(ProtectionSettings.ANTIBOT_DURATION);
        flaggedCounter = new AtomicIntervalCounter(settings.getProperty(ProtectionSettings.ANTIBOT_SENSIBILITY),
            settings.getProperty(ProtectionSettings.ANTIBOT_INTERVAL) * 1000);

        stopProtection();
        antiBotStatus = AntiBotStatus.DISABLED;

        if (!settings.getProperty(ProtectionSettings.ENABLE_ANTIBOT)) {
            return;
        }

        Runnable enableTask = () -> antiBotStatus = AntiBotStatus.LISTENING;

        // Delay the schedule on first start
        if (startup) {
            bukkitService.runTaskLaterOnGlobalRegion(enableTask,
                settings.getProperty(ProtectionSettings.ANTIBOT_DELAY) * TICKS_PER_SECOND);
            startup = false;
        } else {
            enableTask.run();
        }
    }

    /**
     * Transitions the anti bot service to an active status.
     */
    // Serialized: startProtection may be reached from the async pre-login thread at the same
    // time as stopProtection is reached from a scheduled region task or a command.
    private synchronized void startProtection() {
        if (antiBotStatus == AntiBotStatus.ACTIVE) {
            return; // Already activating/active
        }
        if (disableTask != null) {
            disableTask.cancel();
        }
        // Schedule auto-disable
        disableTask = bukkitService.runTaskLaterOnGlobalRegion(this::stopProtection, duration * TICKS_PER_MINUTE);
        antiBotStatus = AntiBotStatus.ACTIVE;
        notifyAdmins(MessageKey.ANTIBOT_AUTO_ENABLED_MESSAGE);
    }

    /**
     * Transitions the anti bot service from active status back to listening.
     */
    private synchronized void stopProtection() {
        if (antiBotStatus != AntiBotStatus.ACTIVE) {
            return;
        }

        // Change status
        antiBotStatus = AntiBotStatus.LISTENING;
        flaggedCounter.reset();

        // Cancel auto-disable task; it may already have been cleared by a reload
        if (disableTask != null) {
            disableTask.cancel();
            disableTask = null;
        }

        // Inform admins
        notifyAdmins(MessageKey.ANTIBOT_AUTO_DISABLED_MESSAGE, Integer.toString(duration));
    }

    /**
     * Returns the status of the AntiBot service.
     *
     * @return status of the antibot service
     */
    public AntiBotStatus getAntiBotStatus() {
        return antiBotStatus;
    }

    /**
     * Allows to override the status of the protection.
     *
     * @param started the new protection status
     */
    public void overrideAntiBotStatus(boolean started) {
        if (antiBotStatus != AntiBotStatus.DISABLED) {
            if (started) {
                startProtection();
            } else {
                stopProtection();
            }
        }
    }

    /**
     * Returns if a player should be kicked due to antibot service.
     *
     * @return if the player should be kicked
     */
    public boolean shouldKick() {
        // Read the volatile status once: this runs on every player login.
        AntiBotStatus status = antiBotStatus;
        if (status == AntiBotStatus.DISABLED) {
            return false;
        } else if (status == AntiBotStatus.ACTIVE) {
            return true;
        }

        if (flaggedCounter.handle()) {
            startProtection();
            return true;
        }
        return false;
    }

    private void notifyAdmins(MessageKey key, String... args) {
        bukkitService.runOnGlobalRegion(() -> {
            for (Player player : bukkitService.getOnlinePlayers()) {
                bukkitService.scheduleSyncTaskFromOptionallyAsyncTask(player, () -> {
                    if (permissionsManager.hasPermission(player, AdminPermission.ANTIBOT_MESSAGES)) {
                        messages.send(player, key, args);
                    }
                });
            }
        });
    }

    public enum AntiBotStatus {
        LISTENING,
        DISABLED,
        ACTIVE
    }

}
