package fr.xephi.authme.velocity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VelocityConfigManagerTest {

    @TempDir
    private Path tempDirectory;

    private VelocityProxyConfiguration loadConfiguration(String yaml) throws IOException {
        Files.writeString(tempDirectory.resolve("config.yml"), yaml);
        return new VelocityConfigManager(tempDirectory).getConfiguration();
    }

    @Test
    void shouldCreateConfigFileWithDefaults() {
        VelocityConfigManager configManager = new VelocityConfigManager(tempDirectory);
        assertTrue(Files.exists(tempDirectory.resolve("config.yml")));
        assertTrue(configManager.getConfiguration().authServers().contains("lobby"));
        assertFalse(configManager.getConfiguration().allServersAreAuthServers());
        assertTrue(configManager.getConfiguration().serverSwitchRequiresAuth());
        assertEquals("Authentication required.", configManager.getConfiguration().serverSwitchKickMessage());
        assertFalse(configManager.getConfiguration().autoLoginEnabled());
        assertTrue(configManager.getConfiguration().commandsRequireAuth());
        assertTrue(configManager.getConfiguration().isWhitelistedCommand("login"));
        assertTrue(configManager.getConfiguration().isWhitelistedCommand("/login"));
        assertTrue(configManager.getConfiguration().chatRequiresAuth());
        assertFalse(configManager.getConfiguration().sharedSecret().isEmpty());
        assertFalse(configManager.getConfiguration().keepOfflineUuidCompatibility());
    }

    @Test
    void shouldPreserveExistingSharedSecret() throws IOException {
        VelocityProxyConfiguration configuration = loadConfiguration("proxySharedSecret: my-existing-secret\n");

        assertEquals("my-existing-secret", configuration.sharedSecret());
    }

    @Test
    void shouldNormalizeConfiguredServersAndLogoutTarget() throws IOException {
        VelocityProxyConfiguration configuration = loadConfiguration("""
authServers:
- Lobby
- HUB
allServersAreAuthServers: false
serverSwitch:
  requiresAuth: false
  kickMessage: Please authenticate first.
autoLogin: true
sendOnLogout: true
unloggedUserServer: LiMbO
premium:
  keepOfflineUuidCompatibility: true
""");

        assertTrue(configuration.authServers().contains("lobby"));
        assertTrue(configuration.authServers().contains("hub"));
        assertFalse(configuration.serverSwitchRequiresAuth());
        assertEquals("Please authenticate first.", configuration.serverSwitchKickMessage());
        assertTrue(configuration.autoLoginEnabled());
        assertTrue(configuration.sendOnLogoutEnabled());
        assertEquals("limbo", configuration.sendOnLogoutTarget());
        assertTrue(configuration.keepOfflineUuidCompatibility());
    }

    @Test
    void shouldNormalizeCommandWhitelist() throws IOException {
        VelocityProxyConfiguration configuration = loadConfiguration("""
            commands:
              requireAuth: true
              whitelist:
              - login
              - /REG
            chatRequiresAuth: true
            """);

        assertTrue(configuration.commandsRequireAuth());
        assertTrue(configuration.isWhitelistedCommand("login"));
        assertTrue(configuration.isWhitelistedCommand("/login"));
        assertTrue(configuration.isWhitelistedCommand("/REG"));
        assertTrue(configuration.isWhitelistedCommand("reg"));
        assertFalse(configuration.isWhitelistedCommand("spawn"));
        assertTrue(configuration.chatRequiresAuth());
    }
}
