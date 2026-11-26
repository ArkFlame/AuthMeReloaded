package fr.xephi.authme.events;

import org.bukkit.event.Event;

/**
 * The parent of all AuthMe events.
 *
 * <p>The asynchronous flag is fixed at construction and is reported by
 * {@link org.bukkit.event.Event#isAsynchronous()}. Concrete events expose their state only through
 * documented accessors; see the {@link fr.xephi.authme.events} package documentation for the
 * nullability and thread-safety rules shared by every event.
 */
public abstract class CustomEvent extends Event {

    /**
     * Constructor.
     */
    public CustomEvent() {
        this(false);
    }

    /**
     * Constructor, specifying whether the event is asynchronous or not.
     *
     * @param isAsync {@code true} to fire the event asynchronously, false otherwise
     * @see Event#Event(boolean)
     */
    public CustomEvent(boolean isAsync) {
        super(isAsync);
    }

}
