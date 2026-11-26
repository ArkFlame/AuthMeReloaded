package fr.xephi.authme.events;

import org.bukkit.event.Cancellable;

/**
 * Common base for AuthMe events whose outcome listeners may veto.
 * <p>
 * Holds the cancellation flag once for all such events. Subclasses keep only their own
 * event-specific state and may override {@link #setCancelled(boolean)} when cancelling
 * has additional consequences.
 */
public abstract class AbstractCancellableEvent extends CustomEvent implements Cancellable {

    private boolean cancelled;

    /**
     * Constructor for a synchronous event.
     */
    protected AbstractCancellableEvent() {
        super();
    }

    /**
     * Constructor.
     *
     * @param isAsync {@code true} to fire the event asynchronously, false otherwise
     * @see CustomEvent#CustomEvent(boolean)
     */
    protected AbstractCancellableEvent(boolean isAsync) {
        super(isAsync);
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}
