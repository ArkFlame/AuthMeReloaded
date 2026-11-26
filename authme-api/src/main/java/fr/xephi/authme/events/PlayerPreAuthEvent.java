package fr.xephi.authme.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

/**
 * Fired as early as possible during a join, as soon as AuthMe knows whether the joining player is
 * registered and needs to authenticate, and <b>before</b> any session lookup, premium bypass, limbo
 * creation, login/register prompt or dialog.
 * <p>
 * Integrations (for example a premium-account plugin) can listen to this event instead of using the
 * Bukkit join event followed by a scheduler wait:
 * <ul>
 *   <li>{@link #forceLogin()} logs a <i>registered</i> player in without a password. The login prompt is
 *       never shown and the regular {@link LoginEvent} is fired by AuthMe.</li>
 *   <li>{@link #skipAuthentication()} (or {@link #setCancelled(boolean) setCancelled(true)}) makes AuthMe
 *       leave the player alone: no limbo, no prompt, no restrictions. The player is <b>not</b> marked as
 *       logged in, so use it only for players whose authentication is handled elsewhere.</li>
 * </ul>
 * The event is always dispatched synchronously on the player's owning thread: the main thread on
 * Bukkit/Paper and the player's region thread on Folia. {@link #isAsynchronous()} is therefore always
 * {@code false}, and listeners may safely interact with the player. AuthMe waits for every listener to
 * return before continuing the asynchronous join, so the decision made here is final. The event is
 * never called when the player is not subject to authentication (for example unrestricted names).
 */
public class PlayerPreAuthEvent extends AbstractCancellableEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    private final Player player;
    private final boolean registered;
    private final Source source;
    private boolean forceLoginRequested;

    /**
     * Constructor.
     *
     * @param player the joining player
     * @param registered true if the player has an AuthMe account, false if they still have to register
     * @param source how the player reached this server
     * @param isAsync true if the event is called asynchronously, false otherwise
     */
    public PlayerPreAuthEvent(Player player, boolean registered, Source source, boolean isAsync) {
        super(isAsync);
        this.player = player;
        this.registered = registered;
        this.source = source;
    }

    /**
     * Returns the joining player.
     *
     * @return the player
     */
    public Player getPlayer() {
        return player;
    }

    /**
     * Returns whether the player has an AuthMe account.
     *
     * @return true if the player is registered (needs to log in), false if they need to register
     */
    public boolean isRegistered() {
        return registered;
    }

    /**
     * Returns how the player reached this server.
     *
     * @return the source of the join
     */
    public Source getSource() {
        return source;
    }

    /**
     * Requests a passwordless login of the player. Only honored for registered players; for an
     * unregistered player the request is ignored because there is no account to log into.
     * Calling this clears a previous {@link #skipAuthentication()}.
     * This mirrors AuthMe's own force login: the password check is skipped and a TOTP code is not
     * required. AuthMe applies the login once this listener returns, on the asynchronous join
     * continuation; if the player has left or is already logged in by then, nothing happens.
     */
    public void forceLogin() {
        this.forceLoginRequested = true;
        super.setCancelled(false);
    }

    /**
     * Returns whether a listener requested a forced login that AuthMe will actually perform.
     *
     * @return true if the player will be logged in without a password
     */
    public boolean isForceLoginRequested() {
        return forceLoginRequested && registered;
    }

    /**
     * Makes AuthMe skip authentication for this join (no prompt, no limbo). Equivalent to
     * {@code setCancelled(true)}.
     */
    public void skipAuthentication() {
        setCancelled(true);
    }

    /**
     * Cancelling the event skips authentication, see {@link #skipAuthentication()}.
     * A cancelled event cannot also force a login: cancelling clears a pending {@link #forceLogin()}.
     *
     * @param cancel true to skip authentication
     */
    @Override
    public void setCancelled(boolean cancel) {
        super.setCancelled(cancel);
        if (cancel) {
            this.forceLoginRequested = false;
        }
    }

    /**
     * Returns the list of handlers, required by {@link org.bukkit.event.Event}.
     *
     * @return the list of handlers
     */
    public static HandlerList getHandlerList() {
        return handlers;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    /** How the joining player reached this server. */
    public enum Source {
        /** Direct connection to this server. */
        JOIN,
        /** Connection forwarded by a BungeeCord/Velocity proxy that cooperates with AuthMe. */
        PROXY
    }
}
