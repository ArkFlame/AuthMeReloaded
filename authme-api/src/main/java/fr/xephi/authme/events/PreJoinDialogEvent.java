package fr.xephi.authme.events;

import org.bukkit.event.HandlerList;

import java.net.InetAddress;
import java.util.UUID;

/**
 * Fired once for each login or register dialog that AuthMe shows while the player is still in
 * Paper/Folia's asynchronous configuration phase. There is no {@link org.bukkit.entity.Player}
 * available yet, so the player is identified by name, unique id and, when known, client address.
 * <p>
 * Cancelling suppresses that dialog only: the player proceeds to the server and AuthMe's normal
 * post-join login/register flow applies. Cancelling never authenticates the player.
 * <p>
 * The configuration phase is asynchronous, so this event is asynchronous as well
 * ({@link #isAsynchronous()} is {@code true} when AuthMe fires it). Handlers must not use
 * synchronous Bukkit API and should return quickly, because the configuration thread waits for
 * the dialog to be answered.
 * <p>
 * The identity data of this event is captured when it is created and never changes. Callers must handle
 * a {@code null} player id or client address; only the player name is always present.
 */
public class PreJoinDialogEvent extends AbstractCancellableEvent {

    /** Which pre-join dialog is about to be shown. */
    public enum DialogType {
        /** Password prompt for an existing account. */
        LOGIN,
        /** Registration prompt for a new account. */
        REGISTER
    }

    private static final HandlerList handlers = new HandlerList();

    private final String playerName;
    private final UUID playerId;
    private final InetAddress address;
    private final DialogType dialogType;
    private final boolean registered;

    /**
     * @param playerName the player name as supplied by the configuration-phase profile
     * @param playerId the player's unique id, may be {@code null} if the profile has none
     * @param address the client address, may be {@code null} if it is not available yet
     * @param dialogType the dialog AuthMe is about to show
     * @param registered whether the player has a registered account
     * @param isAsync {@code true} because the configuration phase runs off the main thread
     */
    public PreJoinDialogEvent(String playerName, UUID playerId, InetAddress address, DialogType dialogType,
                              boolean registered, boolean isAsync) {
        super(isAsync);
        this.playerName = playerName;
        this.playerId = playerId;
        this.address = address;
        this.dialogType = dialogType;
        this.registered = registered;
    }

    /**
     * @param playerName the player name as supplied by the configuration-phase profile
     * @param playerId the player's unique id, may be {@code null} if the profile has none
     * @param dialogType the dialog AuthMe is about to show
     * @param registered whether the player has a registered account
     * @param isAsync {@code true} because the configuration phase runs off the main thread
     */
    public PreJoinDialogEvent(String playerName, UUID playerId, DialogType dialogType,
                              boolean registered, boolean isAsync) {
        this(playerName, playerId, null, dialogType, registered, isAsync);
    }

    /**
     * Return the name of the player the dialog is for.
     *
     * @return the name of the player the dialog is for, never {@code null}
     */
    public String getPlayerName() {
        return playerName;
    }

    /**
     * Return the unique id of the player the dialog is for.
     *
     * @return the unique id of the player, or {@code null} if the profile has none
     */
    public UUID getPlayerId() {
        return playerId;
    }

    /**
     * Alias for {@link #getPlayerId()}.
     *
     * @return the unique id of the player, or {@code null} if the profile has none
     */
    public UUID getUuid() {
        return getPlayerId();
    }

    /**
     * Return the client address of the player the dialog is for.
     *
     * @return the client address, or {@code null} if it was not available during the configuration phase
     */
    public InetAddress getAddress() {
        return address;
    }

    /**
     * Alias for {@link #getAddress()}.
     *
     * @return the client address, or {@code null} if it was not available during the configuration phase
     */
    public InetAddress getInetAddress() {
        return getAddress();
    }

    /**
     * Return which dialog AuthMe is about to show.
     *
     * @return the dialog AuthMe is about to show, never {@code null}
     */
    public DialogType getDialogType() {
        return dialogType;
    }

    /**
     * Alias for {@link #getDialogType()}.
     *
     * @return the dialog AuthMe is about to show
     */
    public DialogType getType() {
        return getDialogType();
    }

    /**
     * Return whether the player has a registered account, which determines the dialog type.
     *
     * @return {@code true} for a login dialog, {@code false} for a registration dialog
     */
    public boolean isRegistered() {
        return registered;
    }

    /** Returns this event's handler list; never {@code null}. */
    public static HandlerList getHandlerList() {
        return handlers;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }
}
