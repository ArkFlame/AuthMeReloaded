package fr.xephi.authme.datasource;

import fr.xephi.authme.data.auth.PlayerAuth;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Verifies that the premium-UUID methods in {@link SQLite} are synchronized, like their sibling methods:
 * all operations share a single JDBC connection and must therefore be serialized.
 */
class SQLitePremiumSynchronizationTest {

    @Test
    void shouldSynchronizeUpdatePremiumUuid() throws Exception {
        assertThat(isSynchronized("updatePremiumUuid", PlayerAuth.class), is(true));
    }

    @Test
    void shouldSynchronizeGetPremiumUsernames() throws Exception {
        assertThat(isSynchronized("getPremiumUsernames"), is(true));
    }

    private static boolean isSynchronized(String name, Class<?>... parameterTypes) throws Exception {
        Method method = SQLite.class.getMethod(name, parameterTypes);
        return Modifier.isSynchronized(method.getModifiers());
    }
}
