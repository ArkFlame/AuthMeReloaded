package fr.xephi.authme.events;

import org.bukkit.event.Event;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

/** Every event in the API jar must extend CustomEvent and follow the Bukkit handler-list contract. */
class ApiEventsConsistencyTest {

    private static final Class<?>[] EVENTS = {
        AbstractCancellableEvent.class, AbstractTeleportEvent.class, AbstractUnregisterEvent.class,
        AuthMeAsyncPreLoginEvent.class, AuthMeAsyncPreRegisterEvent.class, AuthMeTeleportEvent.class,
        EmailChangedEvent.class,
        FailedLoginEvent.class, FirstSpawnTeleportEvent.class, LoginEvent.class, LogoutEvent.class,
        PlayerPreAuthEvent.class, PreJoinDialogEvent.class, ProtectInventoryEvent.class, RegisterEvent.class,
        RestoreInventoryEvent.class, RestoreSessionEvent.class, SpawnTeleportEvent.class,
        UnregisterByAdminEvent.class, UnregisterByPlayerEvent.class
    };

    static Class<?>[] events() {
        return EVENTS.clone();
    }

    @Test
    void shouldExtendCustomEventAndHaveHandlerList() throws Exception {
        for (Class<?> clazz : EVENTS) {
            assertThat(clazz.getSimpleName(), CustomEvent.class.isAssignableFrom(clazz) && Event.class.isAssignableFrom(clazz),
                equalTo(true));
            Method m = null;
            try {
                m = clazz.getMethod("getHandlerList");
            } catch (NoSuchMethodException ignored) {
                // abstract events have none
            }
            if (Modifier.isAbstract(clazz.getModifiers())) {
                assertThat(clazz.getSimpleName(), m, nullValue());
            } else {
                assertThat(clazz.getSimpleName(), m != null && Modifier.isStatic(m.getModifiers()), equalTo(true));
            }
        }
    }

    @Test
    void eventStateIsOnlyEverHeldInPrivateFields() {
        for (Class<?> clazz : EVENTS) {
            for (Field field : clazz.getDeclaredFields()) {
                if (field.isSynthetic() || field.getName().contains("$")) {
                    continue;
                }
                assertThat(clazz.getSimpleName() + "." + field.getName() + " must be private",
                    Modifier.isPrivate(field.getModifiers()), equalTo(true));
            }
        }
    }
}
