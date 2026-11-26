package fr.xephi.authme;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bukkit instantiates the main class reflectively and requires a public no-args constructor.
 */
class AuthMeMainClassTest {

    @Test
    void shouldHavePublicNoArgsConstructor() throws NoSuchMethodException {
        Constructor<AuthMe> constructor = AuthMe.class.getDeclaredConstructor();
        assertTrue(Modifier.isPublic(constructor.getModifiers()));
    }
}
