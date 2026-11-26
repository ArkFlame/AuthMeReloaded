package fr.xephi.authme.events;

import org.bukkit.event.HandlerList;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

/** Verifies the cancellation contract shared by all cancellable AuthMe events. */
class CancellableEventContractTest {

    private static final HandlerList HANDLERS = new HandlerList();

    private static final class TestEvent extends AbstractCancellableEvent {

        private TestEvent() {
            super();
        }

        private TestEvent(boolean isAsync) {
            super(isAsync);
        }

        @Override
        public HandlerList getHandlers() {
            return HANDLERS;
        }
    }

    @Test
    void cancellationStartsFalseAndToggles() {
        TestEvent event = new TestEvent();
        assertThat(event.isCancelled(), equalTo(false));

        event.setCancelled(true);
        assertThat(event.isCancelled(), equalTo(true));

        event.setCancelled(false);
        assertThat(event.isCancelled(), equalTo(false));
    }

    @Test
    void asyncFlagComesFromConstructor() {
        assertThat(new TestEvent().isAsynchronous(), equalTo(false));
        assertThat(new TestEvent(true).isAsynchronous(), equalTo(true));
    }
}
