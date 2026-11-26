package fr.xephi.authme.events;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

/** Checks the handler-registry contract the AuthMe API offers through Bukkit's event bus. */
class AuthMeApiContractTest {

    private static final Class<?>[] CONCRETE_EVENTS = Arrays.stream(ApiEventsConsistencyTest.events())
        .filter(eventClass -> !Modifier.isAbstract(eventClass.getModifiers()))
        .toArray(Class<?>[]::new);

    @Test
    void everyEventRegistersDistinctHandlersThroughItsStaticList() throws Exception {
        Set<Object> seenLists = new HashSet<>();
        for (Class<?> eventClass : CONCRETE_EVENTS) {
            Object handlerList = eventClass.getMethod("getHandlerList").invoke(null);
            assertThat(eventClass.getSimpleName(), handlerList, notNullValue());

            Object event = mock(eventClass, CALLS_REAL_METHODS);
            assertThat(eventClass.getSimpleName(),
                eventClass.getMethod("getHandlers").invoke(event), sameInstance(handlerList));
            assertThat(eventClass.getSimpleName() + " must not share a handler list", seenLists.add(handlerList),
                equalTo(true));
        }
    }

    @Test
    void everyEventTypeIsPublicApi() {
        for (Class<?> eventClass : ApiEventsConsistencyTest.events()) {
            assertThat(eventClass.getSimpleName(), Modifier.isPublic(eventClass.getModifiers()), equalTo(true));
        }
    }
}
