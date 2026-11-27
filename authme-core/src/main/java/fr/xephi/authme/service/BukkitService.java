package fr.xephi.authme.service;

import fr.xephi.authme.AuthMe;
import fr.xephi.authme.initialization.SettingsDependent;
import fr.xephi.authme.service.bungeecord.MessageType;
import fr.xephi.authme.platform.SchedulingAdapter;
import fr.xephi.authme.settings.Settings;
import fr.xephi.authme.settings.properties.PluginSettings;
import org.bukkit.BanEntry;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import javax.inject.Inject;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.Date;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Service for operations requiring the Bukkit API, such as for scheduling.
 */
public class BukkitService implements SettingsDependent {

    /** Number of ticks per second in the Bukkit main thread. */
    public static final int TICKS_PER_SECOND = 20;
    /** Number of ticks per minute. */
    public static final int TICKS_PER_MINUTE = 60 * TICKS_PER_SECOND;

    private final AuthMe authMe;
    private final SchedulingAdapter schedulingAdapter;
    private boolean useAsyncTasks;

    @Inject
    BukkitService(AuthMe authMe, Settings settings, SchedulingAdapter schedulingAdapter) {
        this.authMe = authMe;
        this.schedulingAdapter = schedulingAdapter;
        reload(settings);
    }

    /**
     * Returns whether the current thread may interact with the given entity.
     *
     * @param entity the entity to check
     * @return true if the entity can be safely accessed from the current thread
     */
    public boolean isOwnedByCurrentThread(Entity entity) {
        return schedulingAdapter.isOwnedByCurrentThread(entity);
    }

    /**
     * Returns whether the current thread may interact with the world at the given location.
     *
     * @param location the location to check
     * @return true if the location can be safely accessed from the current thread
     */
    public boolean isOwnedByCurrentThread(Location location) {
        return schedulingAdapter.isOwnedByCurrentThread(location);
    }

    /**
     * Schedules a task on the owning thread of the given entity.
     *
     * @param entity the entity whose scheduler should own the task
     * @param task the task to run
     */
    public void scheduleSyncTaskFromOptionallyAsyncTask(Entity entity, Runnable task) {
        if (schedulingAdapter.isOwnedByCurrentThread(entity)) {
            task.run();
            return;
        }
        schedulingAdapter.runOnEntityThread(authMe, entity, task);
    }

    public void scheduleSyncDelayedTask(Entity entity, Runnable task, long delay) {
        runTaskLater(entity, task, delay);
    }

    /**
     * Schedules a synchronous task if we are currently on a async thread; if not, it runs the task immediately.
     * Use this when {@link #runTaskOptionallyAsync(Runnable) optionally asynchronous tasks} have to
     * run something synchronously.
     *
     * @param task the task to be run
     */
    public void scheduleSyncTaskFromOptionallyAsyncTask(Runnable task) {
        runOnGlobalRegion(task);
    }

    /**
     * Runs the given supplier on the main thread if necessary and returns its result.
     *
     * @param supplier the supplier to run
     * @param <T> the return type
     * @return the supplier's result
     */
    public <T> T callSyncMethodFromOptionallyAsyncTask(Supplier<T> supplier) {
        if (schedulingAdapter.isGlobalThread()) {
            return supplier.get();
        }

        CompletableFuture<T> future = new CompletableFuture<>();
        runOnGlobalRegion(() -> {
            try {
                future.complete(supplier.get());
            } catch (RuntimeException | Error e) {
                future.completeExceptionally(e);
            }
        });
        return awaitFuture(future);
    }

    private static <T> T awaitFuture(CompletableFuture<T> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for sync task", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new IllegalStateException("Unexpected exception while waiting for sync task", cause);
        }
    }

    /**
     * Schedules a delayed task on the owning thread of the given entity.
     *
     * @param entity the entity whose scheduler should own the task
     * @param task the task to run
     * @param delay the delay in ticks
     * @return the scheduled task handle
     */
    public CancellableTask runTaskLater(Entity entity, Runnable task, long delay) {
        return schedulingAdapter.runDelayedOnEntityThread(authMe, entity, task, delay);
    }

    /**
     * Schedules this task to run asynchronously or immediately executes it based on
     * AuthMe's configuration.
     *
     * @param task the task to run
     */
    public void runTaskOptionallyAsync(Runnable task) {
        if (useAsyncTasks) {
            runTaskAsynchronously(task);
        } else {
            task.run();
        }
    }

    /**
     * Returns a task that will run asynchronously. Asynchronous tasks should never access the Bukkit API.
     *
     * @param task the task to be run
     * @return the scheduled task handle
     */
    public CancellableTask runTaskAsynchronously(Runnable task) {
        return schedulingAdapter.runAsyncTask(authMe, task);
    }

    /**
     * Returns a task that will repeatedly run asynchronously until cancelled, starting after the
     * specified number of server ticks. Asynchronous tasks should never access the Bukkit API.
     *
     * @param task the task to be run
     * @param delay the ticks to wait before running the task for the first time
     * @param period the ticks to wait between runs
     * @return the scheduled task handle
     */
    public CancellableTask runTaskTimerAsynchronously(Runnable task, long delay, long period) {
        return schedulingAdapter.runAsyncTaskTimer(authMe, task, delay, period);
    }

    /**
     * Schedules a repeating task on the owning thread of the given entity.
     *
     * @param entity the entity whose scheduler should own the task
     * @param task the task to schedule
     * @param delay the initial delay in ticks
     * @param period the repeat period in ticks
     * @return the scheduled task handle
     */
    public CancellableTask runTaskTimer(Entity entity, Runnable task, long delay, long period) {
        return schedulingAdapter.runAtFixedRateOnEntityThread(authMe, entity, task, delay, period);
    }

    /**
     * Schedules a task on the global region.
     *
     * @param task the task to run
     */
    public void runOnGlobalRegion(Runnable task) {
        if (schedulingAdapter.isGlobalThread()) {
            task.run();
            return;
        }
        schedulingAdapter.runOnGlobalThread(authMe, task);
    }

    /**
     * Schedules a delayed task on the global region.
     *
     * @param task the task to run
     * @param delay the delay in ticks
     * @return the scheduled task handle
     */
    public CancellableTask runTaskLaterOnGlobalRegion(Runnable task, long delay) {
        return schedulingAdapter.runDelayedOnGlobalThread(authMe, task, delay);
    }

    /**
     * Broadcast a message to all players.
     *
     * @param message the message
     * @return the number of players
     */
    public int broadcastMessage(String message) {
        return Bukkit.broadcastMessage(message);
    }

    /**
     * Gets the player with the exact given name, case insensitive.
     *
     * @param name Exact name of the player to retrieve
     * @return a player object if one was found, null otherwise
     */
    public Player getPlayerExact(String name) {
        return authMe.getServer().getPlayerExact(name);
    }

    /**
     * Gets the player by the given name, regardless if they are offline or online.
     *
     * @param name the name the player to retrieve
     * @return an offline player
     */
    public OfflinePlayer getOfflinePlayer(String name) {
        return authMe.getServer().getOfflinePlayer(name);
    }

    /**
     * Gets a set containing all banned players.
     *
     * @return a set containing banned players
     */
    public Set<OfflinePlayer> getBannedPlayers() {
        return Bukkit.getBannedPlayers();
    }

    /**
     * Gets every player that has ever played on this server.
     *
     * @return an array containing all previous players
     */
    public OfflinePlayer[] getOfflinePlayers() {
        return Bukkit.getOfflinePlayers();
    }

    /**
     * Gets a view of all currently online players.
     *
     * @return collection of online players
     */
    @SuppressWarnings("unchecked")
    public Collection<Player> getOnlinePlayers() {
        return (Collection<Player>) Bukkit.getOnlinePlayers();
    }

    /**
     * Calls an event with the given details.
     *
     * @param event Event details
     */
    public void callEvent(Event event) {
        Bukkit.getPluginManager().callEvent(event);
    }

    /**
     * Creates an event with the provided function and emits it.
     *
     * @param eventSupplier the event supplier: function taking a boolean specifying whether AuthMe is configured
     *                      in async mode or not
     * @param <E> the event type
     * @return the event that was created and emitted
     */
    public <E extends Event> E createAndCallEvent(Function<Boolean, E> eventSupplier) {
        E event = eventSupplier.apply(useAsyncTasks);
        callEvent(event);
        return event;
    }

    /**
     * Gets the world with the given name.
     *
     * @param name the name of the world to retrieve
     * @return a world with the given name, or null if none exists
     */
    public World getWorld(String name) {
        return Bukkit.getWorld(name);
    }

    /**
     * Dispatches a command on this server, and executes it if found.
     *
     * @param sender the apparent sender of the command
     * @param commandLine the command + arguments. Example: <code>test abc 123</code>
     * @return returns false if no target is found
     */
    public boolean dispatchCommand(CommandSender sender, String commandLine) {
        return Bukkit.dispatchCommand(sender, commandLine);
    }

    /**
     * Dispatches a command to be run as console user on this server, and executes it if found.
     * When not called from the global thread, the command is run later on the global thread.
     *
     * @param commandLine the command + arguments. Example: <code>test abc 123</code>
     * @return false if no target is found (always true when the command is run later)
     */
    public boolean dispatchConsoleCommand(String commandLine) {
        if (schedulingAdapter.isGlobalThread()) {
            return Bukkit.dispatchCommand(Bukkit.getConsoleSender(), commandLine);
        }
        runOnGlobalRegion(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), commandLine));
        return true;
    }

    @Override
    public void reload(Settings settings) {
        useAsyncTasks = settings.getProperty(PluginSettings.USE_ASYNC_TASKS);
    }

    /**
     * Sends the specified bytes to bungeecord using the specified player connection.
     *
     * @param player the player
     * @param bytes the message
     */
    public void sendBungeeMessage(Player player, byte[] bytes) {
        player.sendPluginMessage(authMe, MessageType.BUNGEE_CHANNEL, bytes);
    }

    /**
     * Sends the specified bytes on the dedicated AuthMe plugin messaging channel.
     *
     * @param player the player
     * @param bytes the message
     */
    public void sendAuthMePluginMessage(Player player, byte[] bytes) {
        player.sendPluginMessage(authMe, MessageType.CHANNEL, bytes);
    }

    /**
     * Adds a ban to the list. If a previous ban exists, this will update the previous entry.
     *
     * @param ip the ip of the ban
     * @param reason reason for the ban, null indicates implementation default
     * @param expires date for the ban's expiration (unban), or null to imply forever
     * @param source source of the ban, null indicates implementation default
     * @return the entry for the newly created ban, or the entry for the (updated) previous ban
     */
    public BanEntry banIp(String ip, String reason, Date expires, String source) {
        return Bukkit.getServer().getBanList(BanList.Type.IP).addBan(ip, reason, expires, source);
    }

    /**
     * Creates a PotionEffect with blindness for the given duration in ticks.
     *
     * @param timeoutInTicks duration of the effect in ticks
     * @return blindness potion effect
     */
    public PotionEffect createBlindnessEffect(int timeoutInTicks) {
        return new PotionEffect(PotionEffectType.BLINDNESS, timeoutInTicks, 2);
    }
}
