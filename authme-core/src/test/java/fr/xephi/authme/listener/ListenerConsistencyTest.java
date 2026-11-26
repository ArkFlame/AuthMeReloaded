package fr.xephi.authme.listener;

import fr.xephi.authme.ClassCollector;
import fr.xephi.authme.TestHelper;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Test for verifying that AuthMe listener methods are well-formed.
 */
final class ListenerConsistencyTest {

    private static List<Class<? extends Listener>> classes;

    @BeforeAll
    static void collectListenerClasses() {
        ClassCollector collector = new ClassCollector(TestHelper.SOURCES_FOLDER, TestHelper.PROJECT_ROOT + "listener");
        classes = collector.collectClasses(Listener.class);

        if (classes.isEmpty()) {
            throw new IllegalStateException("Did not find any Listener classes. Is the folder correct?");
        }
    }

    @Test
    void shouldSetIgnoreCancelledToTrue() {
        classes.forEach(ListenerConsistencyTest::checkEventHandlerAttributes);
    }

    @Test
    void shouldHaveOnlyEventListenersAsPublicMembers() {
        classes.forEach(ListenerConsistencyTest::checkPublicMethodsAreListeners);
    }

    // #367: Event listeners with EventPriority.MONITOR should not change events
    @Test
    void shouldNotHaveMonitorLevelEventHandlers() {
        classes.forEach(ListenerConsistencyTest::checkEventHandlerAttributes);
    }

    @Test
    void shouldNotHaveMultipleMethodsWithSameName() {
        Set<String> events = new HashSet<>();
        for (Class<?> listener : classes) {
            for (Method method : listener.getDeclaredMethods()) {
                if (isTestableMethod(method) && events.contains(method.getName())) {
                    fail("More than one method '" + method.getName() + "' exists (e.g. class: " + listener + ")");
                }
                events.add(method.getName());
            }
        }
    }

    private static void checkEventHandlerAttributes(Class<?> listenerClass) {
        final String clazz = listenerClass.getSimpleName();
        for (Method method : listenerClass.getDeclaredMethods()) {
            if (isTestableMethod(method) && method.isAnnotationPresent(EventHandler.class)) {
                EventHandler annotation = method.getAnnotation(EventHandler.class);
                assertThat("Method " + clazz + "#" + method.getName() + " does not use EventPriority.MONITOR",
                    annotation.priority(), not(equalTo(EventPriority.MONITOR)));
                if (method.getParameterTypes()[0].isAssignableFrom(Cancellable.class)) {
                    assertThat("Method " + clazz + "#" + method.getName() + " should have ignoreCancelled = true",
                        annotation.ignoreCancelled(), equalTo(true));
                }
            }
        }
    }

    private static void checkPublicMethodsAreListeners(Class<?> listenerClass) {
        for (Method method : listenerClass.getDeclaredMethods()) {
            if (isTestableMethod(method) && !method.isAnnotationPresent(EventHandler.class)) {
                fail("Expected @EventHandler annotation on non-private method "
                    + listenerClass.getSimpleName() + "#" + method.getName());
            }
        }
    }

    private static boolean isTestableMethod(Method method) {
        // Exclude getters and synthetic methods
        String methodName = method.getName();
        if (Modifier.isPrivate(method.getModifiers()) || method.isSynthetic() || methodName.startsWith("get") || methodName.startsWith("is")) {
            return false;
        }
        // Skip reload() method (implementation of Reloadable interface)
        return !"reload".equals(method.getName()) || method.getParameterTypes().length > 0;
    }

}
