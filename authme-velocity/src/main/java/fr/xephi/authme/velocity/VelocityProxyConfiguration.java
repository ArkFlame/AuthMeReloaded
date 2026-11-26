package fr.xephi.authme.velocity;

import ch.jalu.configme.SettingsManager;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import fr.xephi.authme.velocity.config.VelocityConfigProperties;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

final class VelocityProxyConfiguration {

    private final Set<String> authServers;
    private final boolean allServersAreAuthServers;
    private final boolean serverSwitchRequiresAuth;
    private final String serverSwitchKickMessage;
    private final boolean autoLoginEnabled;
    private final boolean sendOnLogoutEnabled;
    private final String sendOnLogoutTarget;
    private final boolean commandsRequireAuth;
    private final Set<String> commandWhitelist;
    private final boolean chatRequiresAuth;
    private final String loginServer;
    private final String sharedSecret;
    private final boolean keepOfflineUuidCompatibility;

    VelocityProxyConfiguration(Set<String> authServers, boolean allServersAreAuthServers,
                               boolean serverSwitchRequiresAuth, String serverSwitchKickMessage,
                               boolean autoLoginEnabled, boolean sendOnLogoutEnabled,
                               String sendOnLogoutTarget, boolean commandsRequireAuth,
                               Set<String> commandWhitelist, boolean chatRequiresAuth,
                               String loginServer, String sharedSecret,
                               boolean keepOfflineUuidCompatibility) {
        this.authServers = authServers;
        this.allServersAreAuthServers = allServersAreAuthServers;
        this.serverSwitchRequiresAuth = serverSwitchRequiresAuth;
        this.serverSwitchKickMessage = serverSwitchKickMessage;
        this.autoLoginEnabled = autoLoginEnabled;
        this.sendOnLogoutEnabled = sendOnLogoutEnabled;
        this.sendOnLogoutTarget = normalizeServerName(sendOnLogoutTarget);
        this.commandsRequireAuth = commandsRequireAuth;
        this.commandWhitelist = commandWhitelist;
        this.chatRequiresAuth = chatRequiresAuth;
        this.loginServer = normalizeServerName(loginServer);
        this.sharedSecret = sharedSecret;
        this.keepOfflineUuidCompatibility = keepOfflineUuidCompatibility;
    }

    static VelocityProxyConfiguration from(SettingsManager settingsManager) {
        return new VelocityProxyConfiguration(
            normalizeAndCollect(settingsManager.getProperty(VelocityConfigProperties.AUTH_SERVERS),
                VelocityProxyConfiguration::normalizeServerName),
            settingsManager.getProperty(VelocityConfigProperties.ALL_SERVERS_ARE_AUTH_SERVERS),
            settingsManager.getProperty(VelocityConfigProperties.SERVER_SWITCH_REQUIRES_AUTH),
            settingsManager.getProperty(VelocityConfigProperties.SERVER_SWITCH_KICK_MESSAGE),
            settingsManager.getProperty(VelocityConfigProperties.AUTOLOGIN),
            settingsManager.getProperty(VelocityConfigProperties.ENABLE_SEND_ON_LOGOUT),
            settingsManager.getProperty(VelocityConfigProperties.SEND_ON_LOGOUT_TARGET),
            settingsManager.getProperty(VelocityConfigProperties.COMMANDS_REQUIRE_AUTH),
            normalizeAndCollect(settingsManager.getProperty(VelocityConfigProperties.COMMANDS_WHITELIST),
                VelocityProxyConfiguration::normalizeCommandAlias),
            settingsManager.getProperty(VelocityConfigProperties.CHAT_REQUIRES_AUTH),
            settingsManager.getProperty(VelocityConfigProperties.LOGIN_SERVER),
            settingsManager.getProperty(VelocityConfigProperties.PROXY_SHARED_SECRET),
            settingsManager.getProperty(VelocityConfigProperties.PREMIUM_KEEP_OFFLINE_UUID_COMPATIBILITY));
    }

    Set<String> authServers() {
        return authServers;
    }

    boolean allServersAreAuthServers() {
        return allServersAreAuthServers;
    }

    boolean serverSwitchRequiresAuth() {
        return serverSwitchRequiresAuth;
    }

    String serverSwitchKickMessage() {
        return serverSwitchKickMessage;
    }

    boolean autoLoginEnabled() {
        return autoLoginEnabled;
    }

    boolean sendOnLogoutEnabled() {
        return sendOnLogoutEnabled;
    }

    String sendOnLogoutTarget() {
        return sendOnLogoutTarget;
    }

    boolean commandsRequireAuth() {
        return commandsRequireAuth;
    }

    boolean chatRequiresAuth() {
        return chatRequiresAuth;
    }

    String loginServer() {
        return loginServer;
    }

    String sharedSecret() {
        return sharedSecret;
    }

    boolean keepOfflineUuidCompatibility() {
        return keepOfflineUuidCompatibility;
    }

    boolean isAuthServer(RegisteredServer server) {
        return allServersAreAuthServers || authServers.contains(normalizeServerName(server.getServerInfo().getName()));
    }

    boolean isWhitelistedCommand(String command) {
        return commandWhitelist.contains(normalizeCommandAlias(command));
    }

    /** Normalizes a collection of values, dropping empty results while preserving input order. */
    private static Set<String> normalizeAndCollect(Collection<String> values, Function<String, String> normalizer) {
        LinkedHashSet<String> normalizedValues = new LinkedHashSet<>();
        for (String value : values) {
            String normalizedValue = normalizer.apply(value);
            if (!normalizedValue.isEmpty()) {
                normalizedValues.add(normalizedValue);
            }
        }
        return Set.copyOf(normalizedValues);
    }

    private static String normalizeServerName(String serverName) {
        return serverName.trim().toLowerCase(Locale.ROOT);
    }

    static String normalizeCommandAlias(String command) {
        String trimmedCommand = command.trim();
        if (trimmedCommand.isEmpty()) {
            return "";
        }
        int firstWhitespace = trimmedCommand.indexOf(' ');
        String commandAlias = firstWhitespace >= 0 ? trimmedCommand.substring(0, firstWhitespace) : trimmedCommand;
        if (!commandAlias.startsWith("/")) {
            commandAlias = "/" + commandAlias;
        }
        return commandAlias.toLowerCase(Locale.ROOT);
    }
}
