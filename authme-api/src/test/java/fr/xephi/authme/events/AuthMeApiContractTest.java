package fr.xephi.authme.events;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

/** Checks the handler-registry contract the AuthMe API offers through Bukkit's event bus. */
class AuthMeApiContractTest {

    private static final Class<?>[] CONCRETE_EVENTS = {
        AuthMeAsyncPreLoginEvent.class, AuthMeAsyncPreRegisterEvent.class, AuthMeTeleportEvent.class,
        EmailChangedEvent.class, FailedLoginEvent.class, FirstSpawnTeleportEvent.class, LoginEvent.class,
        LogoutEvent.class, PlayerPreAuthEvent.class, PreJoinDialogEvent.class, ProtectInventoryEvent.class,
        RegisterEvent.class, RestoreInventoryEvent.class, RestoreSessionEvent.class, SpawnTeleportEvent.class,
        UnregisterByAdminEvent.class, UnregisterByPlayerEvent.class
    };

    @Test
    void everyEventRegistersHandlersThroughItsStaticList() throws Exception {
        for (Class<?> eventClass : CONCRETE_EVENTS) {
            Object handlerList = eventClass.getMethod("getHandlerList").invoke(null);
            assertThat(eventClass.getSimpleName(), handlerList, notNullValue());

            Method getHandlers = eventClass.getMethod("getHandlers");
            Object event = mock(eventClass, CALLS_REAL_METHODS);
            assertThat(eventClass.getSimpleName(), getHandlers.invoke(event), sameInstance(handlerList));
        }
    }
}
