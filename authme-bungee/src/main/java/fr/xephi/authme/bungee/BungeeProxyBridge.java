package fr.xephi.authme.bungee;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import fr.xephi.authme.bungee.events.AuthMeBungeeLoginEvent;
import fr.xephi.authme.bungee.events.AuthMeBungeeLogoutEvent;
import fr.xephi.authme.bungee.premium.BungeePremiumOnlineModeHandler;
import fr.xephi.authme.bungee.premium.PremiumVerificationManager;
import fr.xephi.authme.bungee.premium.PremiumVerificationManagers;
import fr.xephi.authme.proxy.ProxyMessageSecurity;
import fr.xephi.authme.proxy.ProxyMessageType;
import fr.xephi.authme.proxy.ProxyMessages;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.event.ChatEvent;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.PreLoginEvent;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.event.ServerConnectEvent;
import net.md_5.bungee.api.event.ServerSwitchEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.UUID;
import java.util.logging.Logger;

public final class BungeeProxyBridge implements Listener {

    static final String AUTHME_CHANNEL = "authme:main";

    private static final String PROXY_IDENTITY = "bungee";
    private static final int MAX_RETRIES = 3;
    private static final ParsedPluginMessage INVALID_MESSAGE = new ParsedPluginMessage(null, null);

    private final ProxyServer proxyServer;
    private final Logger logger;
    // Replaced by /abreloadproxy on the main thread and read by plugin-message and retry threads.
    private volatile BungeeProxyConfiguration configuration;
    private final BungeeAuthenticationStore authenticationStore;
    private final Map<String, AtomicInteger> pendingAutoLogins = new ConcurrentHashMap<>();
    private final Set<String> notifiedAuthServers = ConcurrentHashMap.newKeySet();
    private final Set<String> handshakeRetriesPending = ConcurrentHashMap.newKeySet();
    private final PremiumNames premiumNames;
    // Players with a pending premium verification (ran /premium but not yet confirmed via reconnect)
    private volatile Set<String> pendingPremiumUsernames = ConcurrentHashMap.newKeySet();
    private final BungeePremiumOnlineModeHandler premiumOnlineModeHandler;
    private final PremiumVerificationManager premiumVerificationManager;
    private final ScheduledExecutorService retryScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "authme-bungee-retry");
        t.setDaemon(true);
        return t;
    });

    BungeeProxyBridge(ProxyServer proxyServer, Logger logger, BungeeProxyConfiguration configuration,
                      BungeeAuthenticationStore authenticationStore, Path dataDirectory) {
        this.proxyServer = proxyServer;
        this.logger = logger;
        this.configuration = configuration;
        this.authenticationStore = authenticationStore;
        this.premiumNames = new PremiumNames(dataDirectory, logger, retryScheduler);
        this.premiumOnlineModeHandler = new BungeePremiumOnlineModeHandler(this::requiresPremiumVerification);
        this.premiumVerificationManager =
            PremiumVerificationManagers.create(proxyServer, logger,
                this::requiresPremiumVerification, name -> pendingPremiumUsernames.contains(name),
                this::clearPendingPremiumVerification,
                () -> this.configuration.keepOfflineUuidCompatibility());
    }

    void reload(BungeeProxyConfiguration configuration) {
        this.configuration = configuration;
        premiumVerificationManager.refreshRegistration();
        logger.info("Configuration reloaded");
    }

    void logConfigurationDetails() {
        if (configuration.allServersAreAuthServers()) {
            logger.info("All registered backend servers are treated as auth servers");
        } else if (configuration.authServers().isEmpty()) {
            logger.warning("No auth servers are configured; autoLogin will only work after authServers is populated"
                + " or allServersAreAuthServers is enabled");
        } else {
            logger.info("Current auth servers:");
            configuration.authServers().forEach(serverName -> logger.info("> " + serverName));
        }

        if (!configuration.autoLoginEnabled()) {
            logger.info("autoLogin is disabled");
        }

        logger.info("premium.keepOfflineUuidCompatibility is "
            + (configuration.keepOfflineUuidCompatibility() ? "enabled" : "disabled"));

        if (configuration.sendOnLogoutEnabled() && configuration.sendOnLogoutTarget().isEmpty()) {
            logger.warning("sendOnLogout is enabled but unloggedUserServer is empty; logout redirects will be skipped");
        }
    }

    void registerChannels() {
        proxyServer.registerChannel(AUTHME_CHANNEL);
        logger.info("Registered AuthMe BungeeCord bridge channel");
        broadcastProxyStartedHandshake();
        premiumVerificationManager.register();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(PreLoginEvent event) {
        if (!configuration.keepOfflineUuidCompatibility()) {
            // PlayerHandshakeEvent runs before LOGIN_START, so the connection name is still null there.
            // PreLoginEvent runs after LOGIN_START and before Bungee chooses the encryption path.
            premiumOnlineModeHandler.enableOnlineModeIfRequired(event.getConnection());
        }
    }

    private boolean requiresPremiumVerification(String normalizedName) {
        return premiumNames.contains(normalizedName) || pendingPremiumUsernames.contains(normalizedName);
    }

    private void clearPendingPremiumVerification(String normalizedName) {
        if (pendingPremiumUsernames.remove(normalizedName)) {
            premiumVerificationManager.clearVerifiedPremium(normalizedName);
            logger.warning("Cleared pending premium verification for '" + normalizedName
                + "' after a failed proxy-side premium handshake");
        }
    }

    private UUID consumeVerifiedPremium(String normalizedName) {
        UUID verifiedPremiumUuid = premiumVerificationManager.getVerifiedPremiumUuid(normalizedName);
        if (verifiedPremiumUuid != null) {
            // A stored UUID means verification succeeded; the pending entry has served its purpose.
            pendingPremiumUsernames.remove(normalizedName);
        }
        return verifiedPremiumUuid;
    }

    void broadcastProxyStartedHandshake() {
        byte[] payload = ProxyMessages.proxyStarted(PROXY_IDENTITY);
        int notified = 0;
        int deferred = 0;
        for (ServerInfo server : proxyServer.getServers().values()) {
            if (!configuration.isAuthServer(server)) {
                continue;
            }
            String serverName = server.getName();
            if (!server.getPlayers().isEmpty()) {
                server.sendData(AUTHME_CHANNEL, payload, false);
                notifiedAuthServers.add(serverName);
                notified++;
                logger.info("Sent proxy.started handshake to auth server '" + serverName + "'");
            } else {
                deferred++;
                logger.info("Deferred proxy.started handshake for '" + serverName + "' (no connected players yet);"
                    + " will retry on first player connection");
            }
        }
        if (notified > 0 || deferred > 0) {
            logger.info("Proxy startup handshake: " + notified + " auth server(s) notified, "
                + deferred + " deferred until first connection");
        }
    }

    @EventHandler
    public void onPluginMessage(PluginMessageEvent event) {
        if (event.isCancelled() || !AUTHME_CHANNEL.equals(event.getTag())) {
            return;
        }
        Object sender = event.getSender();
        event.setCancelled(true);
        if (!(sender instanceof Server server)) {
            logger.fine("Blocked authme:main message from non-server source (client-side spoofing attempt?)");
            return;
        }

        ParsedPluginMessage parsedMessage = parsePluginMessage(event.getData());
        if (parsedMessage.type() == null || parsedMessage.playerName() == null) {
            return;
        }
        handlePluginMessage(parsedMessage, server);
    }

    private void handlePluginMessage(ParsedPluginMessage message, Server server) {
        // Name-bearing messages are already normalized by parsePluginMessage; the premium list
        // messages carry a raw comma-separated payload that each handler normalizes itself.
        String playerName = message.playerName();

        switch (message.type()) {
            case LOGIN -> handleLoginMessage(server, playerName);
            case LOGOUT -> handleLogoutMessage(playerName);
            case PERFORM_LOGIN_ACK -> {
                logger.info("Auto-login ACK received for " + playerName
                    + " from server '" + server.getInfo().getName() + "'");
                pendingAutoLogins.remove(playerName);
            }
            case PREMIUM_SET -> {
                premiumNames.add(playerName);
                pendingPremiumUsernames.remove(playerName);
                logger.fine(() -> "Premium enabled for '" + playerName + "' (proxy cache updated)");
            }
            case PREMIUM_UNSET -> {
                premiumNames.remove(playerName);
                pendingPremiumUsernames.remove(playerName);
                premiumVerificationManager.clearVerifiedPremium(playerName);
                logger.fine(() -> "Premium disabled for '" + playerName + "' (proxy cache updated)");
            }
            case PREMIUM_PENDING_SET -> {
                pendingPremiumUsernames.add(playerName);
                premiumVerificationManager.clearVerifiedPremium(playerName);
                logger.fine(() -> "Pending premium verification started for '" + playerName + "'");
            }
            case PREMIUM_LIST -> premiumNames.replaceFromCsv(playerName);
            case PREMIUM_LIST_CHUNK -> premiumNames.handleChunk(playerName);
            default -> {
                // Outbound-only message types are never handled on the inbound channel.
            }
        }
    }

    private void handleLoginMessage(Server server, String playerName) {
        if (configuration.isAuthServer(server.getInfo())) {
            logger.info("Player " + playerName + " authenticated on auth server '"
                + server.getInfo().getName() + "'");

            ProxiedPlayer player = proxyServer.getPlayer(playerName);
            if (player != null) {
                boolean premium = requiresPremiumVerification(playerName);
                proxyServer.getPluginManager().callEvent(new AuthMeBungeeLoginEvent(player, premium));
            }

            authenticationStore.markAuthenticated(playerName);

            sendAutoLoginIfAlreadySwitched(playerName, server.getInfo());
            redirectPlayer(playerName, configuration.loginServer(), "authentication", false);
        } else if (pendingAutoLogins.containsKey(playerName)) {
            // Implicit ACK: login from non-auth server confirms perform.login was processed
            logger.info("Auto-login confirmed for " + playerName
                + " via login from server '" + server.getInfo().getName() + "'");
            pendingAutoLogins.remove(playerName);
        }
    }

    private void handleLogoutMessage(String playerName) {
        authenticationStore.markLoggedOut(playerName);
        ProxiedPlayer player = proxyServer.getPlayer(playerName);
        if (player != null) {
            proxyServer.getPluginManager().callEvent(new AuthMeBungeeLogoutEvent(player));
        }

        if (configuration.sendOnLogoutEnabled()) {
            redirectPlayer(playerName, configuration.sendOnLogoutTarget(), "logout", true);
        }
    }

    @EventHandler
    public void onServerSwitch(ServerSwitchEvent event) {
        ProxiedPlayer player = event.getPlayer();
        Server currentServer = player.getServer();
        if (currentServer != null) {
            sendProxyStartedHandshakeIfPending(currentServer.getInfo());
        }
        if (!configuration.autoLoginEnabled() || currentServer == null) {
            return;
        }

        boolean connectingToAuthServer = configuration.isAuthServer(currentServer.getInfo());
        boolean leavingAuthServer = event.getFrom() != null && configuration.isAuthServer(event.getFrom());
        if (!connectingToAuthServer && !leavingAuthServer) {
            return;
        }

        String normalizedName = BungeeAuthenticationStore.normalizeName(player.getName());
        UUID verifiedPremiumUuid = consumeVerifiedPremium(normalizedName);
        boolean isPremiumJoin = connectingToAuthServer && verifiedPremiumUuid != null;
        if (!authenticationStore.isAuthenticated(player) && !isPremiumJoin) {
            return;
        }
        if (isPremiumJoin) {
            pendingPremiumUsernames.remove(normalizedName);
            logger.fine("PacketEvents-verified premium player " + normalizedName
                + " joining auth server — sending perform.login immediately");
        }
        String serverName = currentServer.getInfo().getName();
        logger.info("Sending auto-login request to server '" + serverName + "' for player " + normalizedName);
        sendPerformLogin(normalizedName, currentServer.getInfo(), verifiedPremiumUuid);
        initiatePendingLogin(normalizedName);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(ChatEvent event) {
        if (event.isCancelled() || !event.isCommand() || !configuration.commandsRequireAuth()) {
            return;
        }
        if (!(event.getSender() instanceof ProxiedPlayer player)) {
            return;
        }
        if (shouldBlockUnauthenticated(event, player, true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(ChatEvent event) {
        if (event.isCancelled() || event.isCommand() || !configuration.chatRequiresAuth()) {
            return;
        }
        if (!(event.getSender() instanceof ProxiedPlayer player)) {
            return;
        }
        if (shouldBlockUnauthenticated(event, player, false)) {
            event.setCancelled(true);
        }
    }

    private boolean shouldBlockUnauthenticated(ChatEvent event, ProxiedPlayer player, boolean command) {
        if (authenticationStore.isAuthenticated(player)) {
            return false;
        }
        Server server = player.getServer();
        return server != null && configuration.isAuthServer(server.getInfo())
            && (!command || !configuration.isWhitelistedCommand(event.getMessage()));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerConnectingToServer(ServerConnectEvent event) {
        if (event.isCancelled() || !configuration.serverSwitchRequiresAuth()) {
            return;
        }

        ProxiedPlayer player = event.getPlayer();
        if (authenticationStore.isAuthenticated(player) || configuration.isAuthServer(event.getTarget())) {
            return;
        }

        event.setCancelled(true);
        TextComponent reasonMessage = new TextComponent(configuration.serverSwitchKickMessage());
        reasonMessage.setColor(ChatColor.RED);
        if (player.getServer() == null) {
            player.disconnect(reasonMessage);
        } else {
            player.sendMessage(reasonMessage);
        }
    }

    @EventHandler
    public void onPlayerDisconnect(PlayerDisconnectEvent event) {
        String normalizedName = BungeeAuthenticationStore.normalizeName(event.getPlayer().getName());
        if (pendingAutoLogins.containsKey(normalizedName)) {
            logger.fine("Cancelling pending auto-login for " + normalizedName + " (player disconnected)");
        }
        pendingAutoLogins.remove(normalizedName);
        pendingPremiumUsernames.remove(normalizedName);
        authenticationStore.markLoggedOut(event.getPlayer().getName());
        premiumVerificationManager.clearVerifiedPremium(normalizedName);
        pendingPremiumUsernames.remove(normalizedName);
    }

    void shutdown() {
        proxyServer.unregisterChannel(AUTHME_CHANNEL);
        premiumVerificationManager.shutdown();
        retryScheduler.shutdownNow();
    }

    private void sendAutoLoginIfAlreadySwitched(String normalizedName, ServerInfo authServer) {
        if (!configuration.autoLoginEnabled()) {
            return;
        }
        ProxiedPlayer player = proxyServer.getPlayer(normalizedName);
        if (player == null) {
            return;
        }
        Server currentConn = player.getServer();
        if (currentConn == null) {
            return;
        }
        if (currentConn.getInfo().equals(authServer)) {
            // Still on auth server — normal flow, ServerSwitchEvent will handle it on switch
            return;
        }

        String currentServerName = currentConn.getInfo().getName();
        logger.info("Player " + normalizedName + " already on server '" + currentServerName
            + "' when login message arrived — sending auto-login immediately");
        UUID verifiedPremiumUuid = consumeVerifiedPremium(normalizedName);
        sendPerformLogin(normalizedName, currentConn.getInfo(), verifiedPremiumUuid);
        initiatePendingLogin(normalizedName);
    }

    private void sendProxyStartedHandshakeIfPending(ServerInfo server) {
        if (!configuration.isAuthServer(server)) {
            return;
        }
        String serverName = server.getName();
        if (!notifiedAuthServers.add(serverName)) {
            return;
        }
        if (!server.getPlayers().isEmpty()) {
            server.sendData(AUTHME_CHANNEL, ProxyMessages.proxyStarted(PROXY_IDENTITY), false);
            logger.info("Sent deferred proxy.started handshake to auth server '" + serverName + "'");
        } else {
            notifiedAuthServers.remove(serverName);
            logger.info("Failed to send deferred proxy.started handshake to '" + serverName + "'; scheduling retry");
            scheduleHandshakeRetry(server, serverName);
        }
    }

    private void scheduleHandshakeRetry(ServerInfo server, String serverName) {
        if (!handshakeRetriesPending.add(serverName)) {
            return;
        }
        retryScheduler.schedule(() -> {
            handshakeRetriesPending.remove(serverName);
            if (!notifiedAuthServers.contains(serverName)) {
                sendProxyStartedHandshakeIfPending(server);
            }
        }, 1, TimeUnit.SECONDS);
    }

    private void initiatePendingLogin(String normalizedName) {
        AtomicInteger attempts = new AtomicInteger(0);
        pendingAutoLogins.put(normalizedName, attempts);
        scheduleRetry(normalizedName, attempts);
    }

    private void scheduleRetry(String normalizedName, AtomicInteger attempts) {
        retryScheduler.schedule(() -> {
            // A retry from an older pending login must not consume the counter of a newer one.
            if (pendingAutoLogins.get(normalizedName) != attempts) {
                return;
            }
            int current = attempts.getAndIncrement();
            if (current >= MAX_RETRIES) {
                pendingAutoLogins.remove(normalizedName);
                logger.warning("No auto-login ACK received for " + normalizedName
                    + " after " + MAX_RETRIES + " retries; giving up");
                return;
            }
            ProxiedPlayer player = proxyServer.getPlayer(normalizedName);
            if (player == null) {
                pendingAutoLogins.remove(normalizedName);
                logger.fine("Auto-login retry cancelled for " + normalizedName + " (player no longer online)");
                return;
            }
            Server server = player.getServer();
            if (server == null) {
                pendingAutoLogins.remove(normalizedName);
                logger.fine("Auto-login retry cancelled for " + normalizedName + " (player has no active server)");
                return;
            }

            String serverName = server.getInfo().getName();
            logger.fine("Retrying auto-login for " + normalizedName + " on server '" + serverName
                + "' (attempt " + (current + 1) + "/" + MAX_RETRIES + ")");
            UUID verifiedPremiumUuid = consumeVerifiedPremium(normalizedName);
            sendPerformLogin(normalizedName, server.getInfo(), verifiedPremiumUuid);
            scheduleRetry(normalizedName, attempts);
        }, 1, TimeUnit.SECONDS);
    }

    private void sendPerformLogin(String normalizedName, ServerInfo serverInfo, UUID verifiedPremiumUuid) {
        long timestamp = System.currentTimeMillis();
        String hmac = ProxyMessageSecurity.computeHmac(
            configuration.sharedSecret(), normalizedName, timestamp, verifiedPremiumUuid);
        byte[] payload = ProxyMessages.performLogin(ProxyMessageType.PERFORM_LOGIN.id(),
            normalizedName, timestamp, verifiedPremiumUuid, hmac);
        serverInfo.sendData(AUTHME_CHANNEL, payload, false);
    }

    private ParsedPluginMessage parsePluginMessage(byte[] data) {
        if (data == null) {
            logger.warning("Received AuthMe plugin message without a payload on the authme:main channel");
            return INVALID_MESSAGE;
        }
        ByteArrayDataInput input = ByteStreams.newDataInput(data);

        try {
            ProxyMessageType type = ProxyMessageType.fromId(input.readUTF());
            if (type == null || type == ProxyMessageType.PERFORM_LOGIN || type == ProxyMessageType.PROXY_STARTED) {
                return INVALID_MESSAGE;
            }
            String argument = input.readUTF();
            boolean rawArgument = type == ProxyMessageType.PREMIUM_LIST || type == ProxyMessageType.PREMIUM_LIST_CHUNK;
            if (rawArgument) {
                // The premium list payload is a raw CSV/chunk string; an empty value is authoritative.
                return new ParsedPluginMessage(type, argument);
            }
            String normalizedName = BungeeAuthenticationStore.normalizeName(argument);
            if (normalizedName.isEmpty()) {
                logger.warning("Ignoring AuthMe plugin message '" + type.id() + "' without a player name");
                return INVALID_MESSAGE;
            }
            return new ParsedPluginMessage(type, normalizedName);
        } catch (IllegalStateException e) {
            logger.warning("Received malformed AuthMe plugin message on the authme:main channel");
            return INVALID_MESSAGE;
        }
    }

    /** Shared redirect path for the post-login and post-logout server transfers. */
    private void redirectPlayer(String normalizedPlayerName, String target, String reason, boolean warnIfNoTarget) {
        if (target.isEmpty()) {
            if (warnIfNoTarget) {
                logger.warning("Received logout for " + normalizedPlayerName
                    + " but sendOnLogout has no configured target server");
            }
            return;
        }
        ProxiedPlayer player = proxyServer.getPlayer(normalizedPlayerName);
        if (player == null) {
            logger.fine("Cannot redirect " + normalizedPlayerName + " to " + reason
                + ": player no longer on proxy");
            return;
        }
        ServerInfo targetServer = proxyServer.getServerInfo(target);
        if (targetServer == null) {
            logger.warning("Target server '" + target + "' is not registered on the proxy;"
                + " cannot redirect " + normalizedPlayerName + " after " + reason);
            return;
        }
        logger.info("Redirecting " + normalizedPlayerName + " to server '" + target + "' after " + reason);
        player.connect(targetServer);
    }

    private record ParsedPluginMessage(ProxyMessageType type, String playerName) {
    }
}
