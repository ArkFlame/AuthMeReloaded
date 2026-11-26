package fr.xephi.authme.bungee;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for cross-thread visibility of the bridge configuration.
 */
class BungeeProxyBridgeConfigurationVisibilityTest {

    @Test
    void configurationMustBeVisibleAcrossProxyThreads() throws Exception {
        Field field = BungeeProxyBridge.class.getDeclaredField("configuration");
        assertTrue(Modifier.isVolatile(field.getModifiers()),
            "configuration is replaced by /abreloadproxy on the main thread and read by"
                + " plugin-message and retry scheduler threads");
    }
}
