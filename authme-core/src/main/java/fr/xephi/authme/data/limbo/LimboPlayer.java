package fr.xephi.authme.data.limbo;

import fr.xephi.authme.task.MessageTask;
import fr.xephi.authme.service.CancellableTask;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Represents a player which is not logged in and keeps track of certain states (like OP status, flying)
 * which may be revoked from the player until he has logged in or registered.
 */
public class LimboPlayer {

    public static final float DEFAULT_WALK_SPEED = 0.2f;
    public static final float DEFAULT_FLY_SPEED = 0.1f;

    private final boolean canFly;
    private final boolean operator;
    private final Collection<UserGroup> groups;
    private final Location loc;
    private final float walkSpeed;
    private final float flySpeed;
    private CancellableTask timeoutTask;
    private MessageTask messageTask;
    private CancellableTask messageTaskHandle;
    private LimboPlayerState state = LimboPlayerState.PASSWORD_REQUIRED;
    private int failedTotpAttempts;
    // The pearl UUIDs are the keys of this map; a separate set would duplicate the same state.
    private Map<UUID, EnderPearlRestoreData> enderPearls = Collections.emptyMap();
    private UUID vehicleUuid;
    private EntityType vehicleType;

    public LimboPlayer(Location loc, boolean operator, Collection<UserGroup> groups, boolean fly, float walkSpeed,
                       float flySpeed) {
        this.loc = loc;
        this.operator = operator;
        this.groups = new ArrayList<>(groups); // prevent bug #2413
        this.canFly = fly;
        this.walkSpeed = walkSpeed;
        this.flySpeed = flySpeed;
    }

    public Location getLocation() {
        return loc;
    }

    public boolean isOperator() {
        return operator;
    }

    public Collection<UserGroup> getGroups() {
        return groups;
    }

    public boolean isCanFly() {
        return canFly;
    }

    public float getWalkSpeed() {
        return walkSpeed;
    }

    public float getFlySpeed() {
        return flySpeed;
    }

    public CancellableTask getTimeoutTask() {
        return timeoutTask;
    }

    /**
     * Sets the task kicking the player if he doesn't log in or register in time, cancelling any previous one.
     */
    public void setTimeoutTask(CancellableTask timeoutTask) {
        if (this.timeoutTask != null) {
            this.timeoutTask.cancel();
        }
        this.timeoutTask = timeoutTask;
    }

    public MessageTask getMessageTask() {
        return messageTask;
    }

    /**
     * Sets the task reminding the player to log in or register, cancelling any previous one.
     */
    public void setMessageTask(MessageTask messageTask, CancellableTask messageTaskHandle) {
        if (this.messageTaskHandle != null) {
            this.messageTaskHandle.cancel();
        }
        this.messageTask = messageTask;
        this.messageTaskHandle = messageTaskHandle;
    }

    public void setMessageTask(MessageTask messageTask) {
        setMessageTask(messageTask, messageTask == null ? null : messageTask::cancel);
    }

    /**
     * Clears all tasks associated to the player.
     */
    public void clearTasks() {
        setMessageTask(null, null);
        setTimeoutTask((CancellableTask) null);
    }

    public LimboPlayerState getState() {
        return state;
    }

    public void setState(LimboPlayerState state) {
        this.state = state;
    }

    /**
     * Records a failed attempt at entering the TOTP code.
     *
     * @return the number of failed TOTP attempts, including this one
     */
    public int increaseFailedTotpAttempts() {
        return ++failedTotpAttempts;
    }

    /**
     * Returns the entity UUIDs of the tracked ender pearls (never null).
     */
    public Set<UUID> getEnderPearlUuids() {
        return enderPearls.keySet();
    }

    /**
     * Returns the tracked ender pearls, including the data required to recreate them.
     */
    public Collection<EnderPearlRestoreData> getEnderPearls() {
        return enderPearls.values();
    }

    /**
     * Returns a mutable copy of the tracked ender pearls keyed by entity UUID, so callers can remove
     * restored pearls without modifying this limbo snapshot.
     */
    Map<UUID, EnderPearlRestoreData> copyEnderPearlsByUuid() {
        return new HashMap<>(enderPearls);
    }

    /**
     * Sets the tracked ender pearls for this player.
     */
    public void setEnderPearls(Collection<EnderPearlRestoreData> pearls) {
        Map<UUID, EnderPearlRestoreData> pearlMap = new HashMap<>();
        for (EnderPearlRestoreData pearl : pearls) {
            if (pearl != null) {
                pearlMap.put(pearl.getUuid(), pearl);
            }
        }
        this.enderPearls = pearlMap.isEmpty() ? Collections.emptyMap() : pearlMap;
    }

    public UUID getVehicleUuid() {
        return vehicleUuid;
    }

    public EntityType getVehicleType() {
        return vehicleType;
    }

    /**
     * Sets the vehicle to restore on reconnect. Pass null for both arguments to clear.
     */
    public void setVehicle(UUID vehicleUuid, EntityType vehicleType) {
        this.vehicleUuid = vehicleUuid;
        this.vehicleType = vehicleType;
    }
}
