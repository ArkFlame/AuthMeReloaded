package fr.xephi.authme.data.limbo;

import fr.xephi.authme.ConsoleLogger;
import fr.xephi.authme.data.limbo.persistence.LimboPersistence;
import fr.xephi.authme.output.ConsoleLoggerFactory;
import fr.xephi.authme.service.BukkitService;
import fr.xephi.authme.service.CancellableTask;
import fr.xephi.authme.service.TeleportationService;
import fr.xephi.authme.settings.Settings;
import fr.xephi.authme.settings.SpawnLoader;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import javax.inject.Inject;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static fr.xephi.authme.settings.properties.LimboSettings.RESTORE_ALLOW_FLIGHT;
import static fr.xephi.authme.settings.properties.LimboSettings.RECREATE_ENDER_PEARLS;
import static fr.xephi.authme.settings.properties.LimboSettings.RESTORE_FLY_SPEED;
import static fr.xephi.authme.settings.properties.LimboSettings.RESTORE_WALK_SPEED;

/**
 * Service for managing players that are in "limbo," a temporary state players are
 * put in which have joined but not yet logged in.
 */
public class LimboService {

    private final ConsoleLogger logger = ConsoleLoggerFactory.get(LimboService.class);

    private final Map<UUID, LimboPlayer> entries = new ConcurrentHashMap<>();

    private final Set<UUID> pendingOperators = ConcurrentHashMap.newKeySet();

    @Inject
    private Settings settings;

    @Inject
    private LimboPlayerTaskManager taskManager;

    @Inject
    private LimboServiceHelper helper;

    @Inject
    private LimboPersistence persistence;

    @Inject
    private AuthGroupHandler authGroupHandler;

    @Inject
    private SpawnLoader spawnLoader;

    @Inject
    private TeleportationService teleportationService;

    @Inject
    private BukkitService bukkitService;

    LimboService() {
    }

    /**
     * Creates a LimboPlayer for the given player and revokes all "limbo data" from the player.
     *
     * @param player the player to process
     * @param isRegistered whether or not the player is registered
     */
    public void createLimboPlayer(Player player, boolean isRegistered) {
        final String name = player.getName().toLowerCase(Locale.ROOT);
        final UUID uuid = player.getUniqueId();

        LimboPlayer limboFromDisk = persistence.getLimboPlayer(player);
        if (limboFromDisk != null) {
            logger.debug("LimboPlayer for `{0}` already exists on disk", name);
        }

        LimboPlayer existingLimbo = entries.remove(uuid);
        if (existingLimbo != null) {
            existingLimbo.clearTasks();
            logger.debug("LimboPlayer for `{0}` already present in memory", name);
        }

        Location location = teleportationService.consumeOriginalJoinLocation(name, spawnLoader.getPlayerLocationOrSpawn(player));
        boolean wasOperator = pendingOperators.remove(uuid);
        LimboPlayer merged = helper.merge(helper.createLimboPlayer(player, isRegistered, location, wasOperator),
            helper.merge(existingLimbo, limboFromDisk));
        LimboPlayer limboPlayer = isRegistered ? merged : helper.withoutOperator(merged);

        LimboMessageType messageType = isRegistered ? LimboMessageType.LOG_IN : LimboMessageType.REGISTER;
        taskManager.registerMessageTask(player, limboPlayer, messageType);
        taskManager.registerTimeoutTask(player, limboPlayer, messageType);
        helper.revokeLimboStates(player);
        authGroupHandler.setGroup(player, limboPlayer,
            isRegistered ? AuthGroupType.REGISTERED_UNAUTHENTICATED : AuthGroupType.UNREGISTERED);
        entries.put(uuid, limboPlayer);
        persistence.saveLimboPlayer(player, limboPlayer);
    }

    /**
     * Revokes the OP status of a player who has just joined, before any of their packets is handled.
     * The status is given back by {@link #createLimboPlayer} (kept in the limbo player) or by
     * {@link #restorePendingOperator}.
     *
     * @param player the player who joined
     */
    public void revokeOperatorOnJoin(Player player) {
        if (player.isOp()) {
            pendingOperators.add(player.getUniqueId());
            player.setOp(false);
        }
    }

    /**
     * Gives back the OP status revoked by {@link #revokeOperatorOnJoin} when no limbo player took it over.
     *
     * @param player the player to restore
     */
    public void restorePendingOperator(Player player) {
        if (pendingOperators.remove(player.getUniqueId())) {
            player.setOp(true);
        }
    }

    /**
     * Returns the limbo player of the given player, or null otherwise.
     *
     * @param player the player to retrieve the data for
     * @return the associated limbo player, or null if none available
     */
    public LimboPlayer getLimboPlayer(Player player) {
        return entries.get(player.getUniqueId());
    }

    /**
     * Returns whether there is a limbo player for the given player.
     *
     * @param player the player to check
     * @return true if present, false otherwise
     */
    public boolean hasLimboPlayer(Player player) {
        return entries.containsKey(player.getUniqueId());
    }

    /**
     * Restores the limbo data and subsequently deletes the entry.
     * <p>
     * Note that teleportation on the player is performed by {@link fr.xephi.authme.service.TeleportationService} and
     * changing the permission group is handled by {@link fr.xephi.authme.data.limbo.AuthGroupHandler}.
     *
     * @param player the player whose data should be restored
     */
    public void restoreData(Player player) {
        String lowerName = player.getName().toLowerCase(Locale.ROOT);
        LimboPlayer limbo = entries.remove(player.getUniqueId());

        if (limbo == null) {
            logger.debug("No LimboPlayer found for `{0}` - cannot restore", lowerName);
            restoreSpeedStates(player, null);
        } else {
            player.setOp(limbo.isOperator());
            restoreSpeedStates(player, limbo);
            limbo.clearTasks();
            logger.debug("Restored LimboPlayer stats for `{0}`", lowerName);
        }
        // Always remove the disk limbo regardless of whether an in-memory limbo was present.
        // If the player quits while in limbo before createLimboPlayer has run (race condition between
        // async join and async quit), the in-memory entry is null but the disk file may still exist.
        // Leaving it would cause the stale location to be reused on the next join.
        persistence.removeLimboPlayer(player);
        authGroupHandler.setGroup(player, limbo, AuthGroupType.LOGGED_IN);
    }

    /**
     * Restores walk/fly speed for a player that has been auto-logged in (premium bypass or session),
     * bypassing the normal limbo lifecycle. Repairs zero speeds left in Bukkit playerdata by a
     * previous session that crashed while the player was unauthenticated, and cleans up any
     * stale disk or in-memory limbo.
     *
     * @param player the auto-logged-in player
     */
    public void restoreSpeedsForAutoLogin(Player player) {
        LimboPlayer diskLimbo = persistence.getLimboPlayer(player);
        restoreSpeedStates(player, diskLimbo);
        if (diskLimbo != null) {
            // Disk limbo captured speeds before revokeLimboStates ran — restore from it
            String lowerName = player.getName().toLowerCase(Locale.ROOT);
            logger.debug("Restored speeds from disk LimboPlayer for `{0}` during auto-login", lowerName);
            doRestoreEntities(player, diskLimbo, lowerName);
        }
        // The player is authenticated, so any in-memory limbo left over from the join flow must
        // be dropped as well: otherwise the entry and its scheduled tasks leak for the session.
        LimboPlayer inMemoryLimbo = entries.remove(player.getUniqueId());
        if (inMemoryLimbo != null) {
            inMemoryLimbo.clearTasks();
        }
        persistence.removeLimboPlayer(player);
    }

    /**
     * Restores walk/fly speed synchronously in the quit event handler, before
     * the player's data is saved to disk. This ensures the .dat file contains
     * the correct speed so the player is never stuck with 0.0f after leaving.
     * <p>
     * Unlike {@link #restoreData(Player)}, this only touches speed and does
     * <b>not</b> remove the in-memory limbo entry ({@code get} vs {@code remove}),
     * so the async quit process can still perform full cleanup afterwards.
     *
     * @param player the player whose speed should be restored
     */
    public void restoreSpeedSync(Player player) {
        LimboPlayer limbo = entries.get(player.getUniqueId());
        if (limbo != null) {
            settings.getProperty(RESTORE_FLY_SPEED).restoreFlySpeed(player, limbo);
            settings.getProperty(RESTORE_WALK_SPEED).restoreWalkSpeed(player, limbo);
        } else if (player.getWalkSpeed() < 0.01f || player.getFlySpeed() < 0.01f) {
            player.setWalkSpeed(LimboPlayer.DEFAULT_WALK_SPEED);
            player.setFlySpeed(LimboPlayer.DEFAULT_FLY_SPEED);
        }
    }

    /** Restores recorded speed states, or corrects zero speeds when no limbo snapshot is available. */
    private void restoreSpeedStates(Player player, LimboPlayer limbo) {
        if (limbo == null) {
            // Without a snapshot the original allowFlight is unknown; only correct stale zero speeds.
            if (player.getWalkSpeed() < 0.01f) {
                player.setWalkSpeed(LimboPlayer.DEFAULT_WALK_SPEED);
            }
            if (player.getFlySpeed() < 0.01f) {
                player.setFlySpeed(LimboPlayer.DEFAULT_FLY_SPEED);
            }
        } else {
            settings.getProperty(RESTORE_ALLOW_FLIGHT).restoreAllowFlight(player, limbo);
            settings.getProperty(RESTORE_FLY_SPEED).restoreFlySpeed(player, limbo);
            settings.getProperty(RESTORE_WALK_SPEED).restoreWalkSpeed(player, limbo);
        }
    }

    /** Returns the player's persisted limbo, or a fresh one capturing his current state if none exists. */
    private LimboPlayer getOrCreateLimbo(Player player) {
        LimboPlayer limbo = persistence.getLimboPlayer(player);
        if (limbo == null) {
            limbo = new LimboPlayer(null, false, Collections.emptyList(), player.getAllowFlight(),
                player.getWalkSpeed(), player.getFlySpeed());
        }
        return limbo;
    }

    /**
     * Re-applies limbo speed and flight restrictions after a player respawns.
     * Bukkit sends a fresh PlayerAbilities packet on respawn that resets walk/fly speed,
     * so the restrictions set during join must be re-applied one tick later.
     *
     * @param player the player to re-apply restrictions for
     */
    public void reapplyLimboRestrictions(Player player) {
        if (hasLimboPlayer(player)) {
            helper.revokeLimboStates(player);
        }
    }

    /**
     * Creates new tasks for the given player and cancels the old ones for a newly registered player.
     * This resets his time to log in (TimeoutTask) and updates the message he is shown (MessageTask).
     *
     * @param player the player to reset the tasks for
     */
    public void replaceTasksAfterRegistration(Player player) {
        Optional<LimboPlayer> limboPlayer = getLimboOrLogError(player, "reset tasks");
        limboPlayer.ifPresent(limbo -> {
            // The register timeout has fired its purpose. Cancel it explicitly: registerTimeoutTask only
            // replaces an existing task when a login timeout is configured, so with loginTimeout=0
            // (timeout disabled) the old register timeout would otherwise still kick the player later.
            limbo.setTimeoutTask((CancellableTask) null);
            taskManager.registerTimeoutTask(player, limbo, LimboMessageType.LOG_IN);
            taskManager.registerMessageTask(player, limbo, LimboMessageType.LOG_IN);
        });
        authGroupHandler.setGroup(player, limboPlayer.orElse(null), AuthGroupType.REGISTERED_UNAUTHENTICATED);
    }

    /**
     * Resets the message task associated with the player's LimboPlayer.
     *
     * @param player the player to set a new message task for
     * @param messageType the message to show for the limbo player
     */
    public void resetMessageTask(Player player, LimboMessageType messageType) {
        getLimboOrLogError(player, "reset message task")
            .ifPresent(limbo -> taskManager.registerMessageTask(player, limbo, messageType));
    }

    /** @param player the player whose message task should be muted */
    public void muteMessageTask(Player player) {
        setMessageTaskMuted(player, true);
    }

    /** @param player the player whose message task should be unmuted */
    public void unmuteMessageTask(Player player) {
        setMessageTaskMuted(player, false);
    }

    private void setMessageTaskMuted(Player player, boolean muted) {
        getLimboOrLogError(player, muted ? "mute message task" : "unmute message task")
            .ifPresent(limbo -> LimboPlayerTaskManager.setMuted(limbo.getMessageTask(), muted));
    }

    /**
     * Returns the limbo player for the given player or logs an error.
     *
     * @param player the player to retrieve the limbo player for
     * @param context the action for which the limbo player is being retrieved (for logging)
     * @return Optional with the limbo player
     */
    private Optional<LimboPlayer> getLimboOrLogError(Player player, String context) {
        LimboPlayer limbo = entries.get(player.getUniqueId());
        if (limbo == null) {
            logger.debug("No LimboPlayer found for `{0}`. Action: {1}", player.getName(), context);
        }
        return Optional.ofNullable(limbo);
    }

    /**
     * Saves the entity UUIDs of in-flight ender pearls to disk so they can be restored
     * to the player on reconnect (stasis chamber support). Called when an authenticated
     * player quits with pearls in flight.
     *
     * @param player the authenticated player who is quitting
     * @param pearls tracked ender pearls of the player's in-flight ender pearls
     */
    public void saveEnderPearlsForPlayer(Player player, Collection<EnderPearlRestoreData> pearls) {
        LimboPlayer limbo = getOrCreateLimbo(player);
        limbo.setEnderPearls(pearls);
        persistence.saveLimboPlayer(player, limbo);
        logger.debug("Saved {0} ender pearl(s) to disk for `{1}`",
            pearls.size(), player.getName().toLowerCase(Locale.ROOT));
    }

    /**
     * Saves the vehicle the player was riding to disk so they can be remounted on reconnect.
     * Called when an authenticated player quits while inside a vehicle.
     *
     * @param player      the authenticated player who is quitting
     * @param vehicleUuid the entity UUID of the vehicle
     * @param vehicleType the entity type of the vehicle
     */
    public void saveVehicleForPlayer(Player player, UUID vehicleUuid, EntityType vehicleType) {
        LimboPlayer limbo = getOrCreateLimbo(player);
        limbo.setVehicle(vehicleUuid, vehicleType);
        persistence.saveLimboPlayer(player, limbo);
        logger.debug("Saved vehicle {0} ({1}) to disk for `{2}`",
            vehicleUuid, vehicleType, player.getName().toLowerCase(Locale.ROOT));
    }

    /**
     * Restores ender pearl shooters and remounts the player's vehicle in a single world pass.
     * Must be called after {@link #createLimboPlayer} so the LimboPlayer is guaranteed to
     * exist in memory.
     *
     * @param player the player whose entities should be restored
     */
    public void restoreEntities(Player player) {
        LimboPlayer limbo = entries.get(player.getUniqueId());
        if (limbo != null) {
            doRestoreEntities(player, limbo, null);
        }
    }

    /**
     * Restores entities using the given already-computed lower-case name, or computes it lazily
     * when {@code name} is null and only after confirming there is anything to restore.
     */
    private void doRestoreEntities(Player player, LimboPlayer limbo, String name) {
        Map<UUID, EnderPearlRestoreData> pendingPearls = limbo.copyEnderPearlsByUuid();
        UUID vehicleUuid = limbo.getVehicleUuid();
        EntityType vehicleType = limbo.getVehicleType();

        if (pendingPearls.isEmpty() && vehicleUuid == null) {
            return;
        }
        String playerName = name != null ? name : player.getName().toLowerCase(Locale.ROOT);

        int pearlTotal = pendingPearls.size();
        int pearlRestored = 0;
        boolean recreateMissingPearls = settings.getProperty(RECREATE_ENDER_PEARLS);
        boolean vehicleDone = vehicleUuid == null;

        outer:
        for (World world : player.getServer().getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (!bukkitService.isOwnedByCurrentThread(entity)) {
                    continue;
                }
                if (!pendingPearls.isEmpty() && entity instanceof EnderPearl
                        && pendingPearls.remove(entity.getUniqueId()) != null) {
                    ((EnderPearl) entity).setShooter(player);
                    pearlRestored++;
                } else if (!vehicleDone
                        && vehicleUuid.equals(entity.getUniqueId())) {
                    entity.addPassenger(player);
                    vehicleDone = true;
                }
                if (pendingPearls.isEmpty() && vehicleDone) {
                    break outer;
                }
            }
        }

        int pearlRecreated = recreateMissingPearls(player, pendingPearls, playerName, recreateMissingPearls);

        limbo.setEnderPearls(Collections.emptyList());
        limbo.setVehicle(null, null);

        logEntityRestoreResult(playerName, pearlRestored, pearlTotal, pearlRecreated,
            vehicleUuid, vehicleType, vehicleDone);
    }

    /**
     * Recreates any pearls that could not be found in the world, unless recreation is disabled in the config.
     *
     * @return the number of pearls successfully recreated
     */
    private int recreateMissingPearls(Player player, Map<UUID, EnderPearlRestoreData> pendingPearls,
                                      String name, boolean enabled) {
        if (!enabled) {
            if (!pendingPearls.isEmpty()) {
                logger.debug("Skipped recreation of {0} missing ender pearl(s) for `{1}` because it is disabled in config",
                    pendingPearls.size(), name);
            }
            return 0;
        }
        int recreated = 0;
        for (EnderPearlRestoreData pearl : pendingPearls.values()) {
            if (recreateEnderPearl(player, pearl, name)) {
                recreated++;
            }
        }
        return recreated;
    }

    /** Logs the outcome of restoring a player's pearls and vehicle. */
    private void logEntityRestoreResult(String name, int pearlRestored, int pearlTotal, int pearlRecreated,
                                        UUID vehicleUuid, EntityType vehicleType, boolean vehicleDone) {
        if (pearlTotal > 0) {
            logger.debug("Restored {0}/{1} ender pearl(s) and recreated {2} for `{3}`",
                pearlRestored, pearlTotal, pearlRecreated, name);
        }
        if (vehicleUuid != null) {
            if (vehicleDone) {
                logger.debug("Restored vehicle ({0}) for `{1}`", vehicleType, name);
            } else {
                logger.debug("Vehicle ({0}) no longer exists for `{1}`, skipping", vehicleType, name);
            }
        }
    }

    private boolean recreateEnderPearl(Player player, EnderPearlRestoreData pearl, String name) {
        if (!pearl.canBeRecreated()) {
            logger.debug("Unable to recreate ender pearl {0} for `{1}` because its saved location is unavailable",
                pearl.getUuid(), name);
            return false;
        }
        if (!bukkitService.isOwnedByCurrentThread(pearl.getLocation())) {
            logger.debug("Unable to recreate ender pearl {0} for `{1}` because its saved location is in another region",
                pearl.getUuid(), name);
            return false;
        }

        EnderPearl recreatedPearl = (EnderPearl) pearl.getLocation().getWorld()
            .spawnEntity(pearl.getLocation(), EntityType.ENDER_PEARL);
        recreatedPearl.setShooter(player);

        Vector velocity = pearl.getVelocity();
        if (velocity != null) {
            recreatedPearl.setVelocity(velocity);
        }
        return true;
    }
}
