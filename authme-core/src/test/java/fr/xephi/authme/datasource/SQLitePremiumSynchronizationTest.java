package fr.xephi.authme.datasource;

import fr.xephi.authme.data.auth.PlayerAuth;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Verifies that the premium-UUID methods in {@link SQLite} are synchronized, like their sibling methods:
 * all operations share a single JDBC connection and must therefore be serialized.
 */
class SQLitePremiumSynchronizationTest {

    @ParameterizedTest
    @MethodSource("premiumMethods")
    void shouldSynchronizePremiumMethods(Method method) {
        assertThat(Modifier.isSynchronized(method.getModifiers()), is(true));
    }

    private static Stream<Method> premiumMethods() throws NoSuchMethodException {
        return Stream.of(
            SQLite.class.getMethod("updatePremiumUuid", PlayerAuth.class),
            SQLite.class.getMethod("getPremiumUsernames"));
    }
}
