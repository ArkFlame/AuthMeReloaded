package fr.xephi.authme.velocity;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AuthMePremium logs premium players in on this proxy by reflecting into
 * {@code plugin.proxyBridge.authenticationStore.markAuthenticated(String)} and by the plugin id
 * "authmevelocity". Renaming any of these silently breaks that auto-login, so they are pinned here.
 */
class ExternalLoginHookContractTest {

    @Test
    void shouldKeepTheReflectivePathUsedByAuthMePremium() throws Exception {
        Field bridge = AuthMeVelocityPlugin.class.getDeclaredField("proxyBridge");
        assertEquals(VelocityProxyBridge.class, bridge.getType());
        Field store = VelocityProxyBridge.class.getDeclaredField("authenticationStore");
        assertEquals(VelocityAuthenticationStore.class, store.getType());
        Method mark = VelocityAuthenticationStore.class.getDeclaredMethod("markAuthenticated", String.class);
        Constructor<VelocityAuthenticationStore> constructor = VelocityAuthenticationStore.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        mark.setAccessible(true);

        VelocityAuthenticationStore instance = constructor.newInstance();
        mark.invoke(instance, "PremiumPlayer");
        mark.invoke(instance, "PremiumPlayer");

        assertTrue(instance.isAuthenticated("premiumplayer"));
    }
}
