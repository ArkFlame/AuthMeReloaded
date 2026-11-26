package fr.xephi.authme.bungee;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AuthMePremium logs premium players in on this proxy by reflecting into
 * {@code plugin.proxyBridge.authenticationStore.markAuthenticated(String)} and by the plugin name
 * "AuthMeBungee". Renaming any of these silently breaks that auto-login, so they are pinned here.
 */
class ExternalLoginHookContractTest {

    @Test
    void shouldKeepTheReflectivePathUsedByAuthMePremium() throws Exception {
        Field bridge = AuthMeBungeePlugin.class.getDeclaredField("proxyBridge");
        assertEquals(BungeeProxyBridge.class, bridge.getType());
        Field store = BungeeProxyBridge.class.getDeclaredField("authenticationStore");
        assertEquals(BungeeAuthenticationStore.class, store.getType());
        Method mark = BungeeAuthenticationStore.class.getDeclaredMethod("markAuthenticated", String.class);
        Constructor<BungeeAuthenticationStore> constructor = BungeeAuthenticationStore.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        mark.setAccessible(true);

        BungeeAuthenticationStore instance = constructor.newInstance();
        mark.invoke(instance, "PremiumPlayer");
        mark.invoke(instance, "PremiumPlayer");

        assertTrue(instance.isAuthenticated("premiumplayer"));
    }
}
