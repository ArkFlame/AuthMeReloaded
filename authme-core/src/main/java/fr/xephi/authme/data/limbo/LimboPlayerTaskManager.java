package fr.xephi.authme.data.limbo;

import fr.xephi.authme.data.auth.PlayerCache;
import fr.xephi.authme.data.captcha.RegistrationCaptchaManager;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.message.Messages;
import fr.xephi.authme.service.BukkitService;
import fr.xephi.authme.service.CancellableTask;
import fr.xephi.authme.settings.Settings;
import fr.xephi.authme.settings.properties.RegistrationSettings;
import fr.xephi.authme.settings.properties.RestrictionSettings;
import fr.xephi.authme.task.MessageTask;
import fr.xephi.authme.task.TimeoutTask;
import org.bukkit.entity.Player;

import javax.inject.Inject;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static fr.xephi.authme.service.BukkitService.TICKS_PER_SECOND;

/**
 * Registers tasks associated with a LimboPlayer.
 */
class LimboPlayerTaskManager {

    private static final MessageResult LOGIN_MESSAGE = new MessageResult(MessageKey.LOGIN_MESSAGE);
    private static final MessageResult TOTP_MESSAGE = new MessageResult(MessageKey.TWO_FACTOR_CODE_REQUIRED);
    private static final MessageResult REGISTER_MESSAGE = new MessageResult(MessageKey.REGISTER_MESSAGE);

    @Inject
    private Messages messages;

    @Inject
    private Settings settings;

    @Inject
    private BukkitService bukkitService;

    @Inject
    private PlayerCache playerCache;

    @Inject
    private RegistrationCaptchaManager registrationCaptchaManager;

    @Inject
    private DataSource dataSource;

    /**
     * Registers a {@link MessageTask} for the given player name.
     *
     * @param player the player
     * @param limbo the associated limbo player of the player
     * @param messageType message type
     */
    void registerMessageTask(Player player, LimboPlayer limbo, LimboMessageType messageType) {
        int interval = settings.getProperty(RegistrationSettings.MESSAGE_INTERVAL);
        MessageResult result = getMessageKey(player.getName(), messageType);
        if (interval > 0) {
            String[] joinMessage = messages.retrieveSingle(player, result.messageKey, result.args).split("\n");
            MessageTask messageTask = new MessageTask(player, joinMessage);
            CancellableTask taskHandle = messageType == LimboMessageType.REGISTER
                ? scheduleRegistrationMessageTask(player, messageTask, interval)
                : bukkitService.runTaskTimer(player, messageTask,
                    toTicks(settings.getProperty(RegistrationSettings.LOGIN_MESSAGE_DELAY)),
                    interval * (long) TICKS_PER_SECOND);
            limbo.setMessageTask(messageTask, taskHandle);
        }
    }

    private static long toTicks(int configuredSeconds) {
        return Math.max(0, configuredSeconds) * (long) TICKS_PER_SECOND;
    }

    private CancellableTask scheduleRegistrationMessageTask(Player player, MessageTask messageTask, int interval) {
        RegistrationMessageTaskHandle taskHandle = new RegistrationMessageTaskHandle(
            player, messageTask, interval * (long) TICKS_PER_SECOND);
        taskHandle.scheduleInitialCheck(toTicks(settings.getProperty(RegistrationSettings.REGISTER_MESSAGE_DELAY)));
        return taskHandle;
    }

    /**
     * Registers a {@link TimeoutTask} for the given player according to the configuration.
     *
     * @param player the player to register a timeout task for
     * @param limbo the associated limbo player
     * @param messageType the limbo message type, used to select login vs. register timeout
     */
    void registerTimeoutTask(Player player, LimboPlayer limbo, LimboMessageType messageType) {
        final int timeout = settings.getProperty(messageType == LimboMessageType.REGISTER
            ? RestrictionSettings.REGISTER_TIMEOUT : RestrictionSettings.LOGIN_TIMEOUT) * TICKS_PER_SECOND;
        if (timeout > 0) {
            String message = messages.retrieveSingle(player, MessageKey.LOGIN_TIMEOUT_ERROR);
            CancellableTask task = bukkitService.runTaskLater(player, new TimeoutTask(player, message, playerCache), timeout);
            limbo.setTimeoutTask(task);
        }
    }

    /**
     * Null-safe method to set the muted flag on a message task.
     *
     * @param task the task to modify (or null)
     * @param isMuted the value to set if task is not null
     */
    static void setMuted(MessageTask task, boolean isMuted) {
        if (task != null) {
            task.setMuted(isMuted);
        }
    }

    /**
     * Returns the appropriate message key according to the registration status and settings.
     *
     * @param name the player's name
     * @param messageType the message to show
     * @return the message key to display to the user
     */
    private MessageResult getMessageKey(String name, LimboMessageType messageType) {
        if (messageType == LimboMessageType.LOG_IN) {
            return LOGIN_MESSAGE;
        }
        if (messageType == LimboMessageType.TOTP_CODE) {
            return TOTP_MESSAGE;
        }
        if (registrationCaptchaManager.isCaptchaRequired(name)) {
            String captchaCode = registrationCaptchaManager.getCaptchaCodeOrGenerateNew(name);
            return new MessageResult(MessageKey.CAPTCHA_FOR_REGISTRATION_REQUIRED, captchaCode);
        }
        return REGISTER_MESSAGE;
    }

    private final class RegistrationMessageTaskHandle implements CancellableTask {
        private final Player player;
        private final MessageTask messageTask;
        private final long intervalTicks;
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final AtomicReference<CancellableTask> currentTask = new AtomicReference<>();

        private RegistrationMessageTaskHandle(Player player, MessageTask messageTask, long intervalTicks) {
            this.player = player;
            this.messageTask = messageTask;
            this.intervalTicks = intervalTicks;
        }

        private void scheduleInitialCheck(long delayTicks) {
            setTask(bukkitService.runTaskLater(player, this::checkRegistrationAsync, delayTicks));
        }

        private void checkRegistrationAsync() {
            if (cancelled.get() || !player.isOnline()) {
                return;
            }
            final String name = player.getName();
            if (playerCache.isAuthenticated(name)) {
                return;
            }

            setTask(bukkitService.runTaskAsynchronously(() -> {
                boolean isRegistered = dataSource.isAuthAvailable(name);
                if (cancelled.get() || isRegistered || playerCache.isAuthenticated(name)) {
                    return;
                }

                bukkitService.scheduleSyncTaskFromOptionallyAsyncTask(player, () -> {
                    if (cancelled.get() || !player.isOnline() || playerCache.isAuthenticated(name)) {
                        return;
                    }

                    messageTask.run();
                    setTask(bukkitService.runTaskTimer(player, messageTask, intervalTicks, intervalTicks));
                });
            }));
        }

        private void setTask(CancellableTask task) {
            currentTask.set(task);
            if (cancelled.get() && currentTask.compareAndSet(task, null)) {
                task.cancel();
            }
        }

        @Override
        public void cancel() {
            if (cancelled.compareAndSet(false, true)) {
                CancellableTask task = currentTask.getAndSet(null);
                if (task != null) {
                    task.cancel();
                }
            }
        }
    }

    private static final class MessageResult {
        private final MessageKey messageKey;
        private final String[] args;

        MessageResult(MessageKey messageKey, String... args) {
            this.messageKey = messageKey;
            this.args = args;
        }
    }
}
