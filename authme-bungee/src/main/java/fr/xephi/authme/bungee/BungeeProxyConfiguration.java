package fr.xephi.authme.bungee;

import ch.jalu.configme.SettingsManager;
import fr.xephi.authme.bungee.config.BungeeConfigProperties;
import net.md_5.bungee.api.config.ServerInfo;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

final class BungeeProxyConfiguration {

    private final Set<String> authServers;
    private final boolean allServersAreAuthServers;
    private final boolean commandsRequireAuth;
    private final Set<String> commandWhitelist;
    private final boolean chatRequiresAuth;
    private final boolean serverSwitchRequiresAuth;
    private final String serverSwitchKickMessage;
    private final boolean autoLoginEnabled;
    private final boolean sendOnLogoutEnabled;
    private final String sendOnLogoutTarget;
    private final String loginServer;
    private final String sharedSecret;
    private final boolean keepOfflineUuidCompatibility;

    BungeeProxyConfiguration(Set<String> authServers, boolean allServersAreAuthServers,
                             boolean commandsRequireAuth, Set<String> commandWhitelist,
                             boolean chatRequiresAuth, boolean serverSwitchRequiresAuth,
                             String serverSwitchKickMessage, boolean autoLoginEnabled,
                             boolean sendOnLogoutEnabled, String sendOnLogoutTarget,
                             String loginServer, String sharedSecret,
                             boolean keepOfflineUuidCompatibility) {
        this.authServers = authServers;
        this.allServersAreAuthServers = allServersAreAuthServers;
        this.commandsRequireAuth = commandsRequireAuth;
        this.commandWhitelist = commandWhitelist;
        this.chatRequiresAuth = chatRequiresAuth;
        this.serverSwitchRequiresAuth = serverSwitchRequiresAuth;
        this.serverSwitchKickMessage = serverSwitchKickMessage;
        this.autoLoginEnabled = autoLoginEnabled;
        this.sendOnLogoutEnabled = sendOnLogoutEnabled;
        this.sendOnLogoutTarget = normalizeServerName(sendOnLogoutTarget);
        this.loginServer = normalizeServerName(loginServer);
        this.sharedSecret = sharedSecret;
        this.keepOfflineUuidCompatibility = keepOfflineUuidCompatibility;
    }

    static BungeeProxyConfiguration from(SettingsManager settingsManager) {
        return new BungeeProxyConfiguration(
            normalizeAndCollect(settingsManager.getProperty(BungeeConfigProperties.AUTH_SERVERS),
                BungeeProxyConfiguration::normalizeServerName),
            settingsManager.getProperty(BungeeConfigProperties.ALL_SERVERS_ARE_AUTH_SERVERS),
            settingsManager.getProperty(BungeeConfigProperties.COMMANDS_REQUIRE_AUTH),
            normalizeAndCollect(settingsManager.getProperty(BungeeConfigProperties.COMMANDS_WHITELIST),
                BungeeProxyConfiguration::normalizeCommandAlias),
            settingsManager.getProperty(BungeeConfigProperties.CHAT_REQUIRES_AUTH),
            settingsManager.getProperty(BungeeConfigProperties.SERVER_SWITCH_REQUIRES_AUTH),
            settingsManager.getProperty(BungeeConfigProperties.SERVER_SWITCH_KICK_MESSAGE),
            settingsManager.getProperty(BungeeConfigProperties.AUTOLOGIN),
            settingsManager.getProperty(BungeeConfigProperties.ENABLE_SEND_ON_LOGOUT),
            settingsManager.getProperty(BungeeConfigProperties.SEND_ON_LOGOUT_TARGET),
            settingsManager.getProperty(BungeeConfigProperties.LOGIN_SERVER),
            settingsManager.getProperty(BungeeConfigProperties.PROXY_SHARED_SECRET),
            settingsManager.getProperty(BungeeConfigProperties.PREMIUM_KEEP_OFFLINE_UUID_COMPATIBILITY));
    }

    Set<String> authServers() {
        return authServers;
    }

    boolean allServersAreAuthServers() {
        return allServersAreAuthServers;
    }

    boolean commandsRequireAuth() {
        return commandsRequireAuth;
    }

    boolean chatRequiresAuth() {
        return chatRequiresAuth;
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

    String loginServer() {
        return loginServer;
    }

    String sharedSecret() {
        return sharedSecret;
    }

    boolean keepOfflineUuidCompatibility() {
        return keepOfflineUuidCompatibility;
    }

    boolean isAuthServer(ServerInfo serverInfo) {
        return allServersAreAuthServers || authServers.contains(normalizeServerName(serverInfo.getName()));
    }

    boolean isWhitelistedCommand(String command) {
        return commandWhitelist.contains(normalizeCommandAlias(command));
    }

    static String normalizeCommandAlias(String command) {
        String trimmedCommand = command.trim();
        if (trimmedCommand.isEmpty()) {
            return "";
        }
        int firstWhitespace = trimmedCommand.indexOf(' ');
        String commandAlias = firstWhitespace >= 0 ? trimmedCommand.substring(0, firstWhitespace) : trimmedCommand;
        return (commandAlias.startsWith("/") ? commandAlias : "/" + commandAlias).toLowerCase(Locale.ROOT);
    }

    /** Normalizes a collection of values, dropping empty results. */
    private static Set<String> normalizeAndCollect(Collection<String> values, Function<String, String> normalizer) {
        return values.stream()
            .map(normalizer)
            .filter(value -> !value.isEmpty())
            .collect(Collectors.toUnmodifiableSet());
    }

    private static String normalizeServerName(String serverName) {
        return serverName.trim().toLowerCase(Locale.ROOT);
    }
}
