package fr.xephi.authme.velocity;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.event.player.GameProfileRequestEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.event.player.configuration.PlayerEnterConfigurationEvent;
import com.velocitypowered.api.event.player.configuration.PlayerEnteredConfigurationEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.LegacyChannelIdentifier;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import fr.xephi.authme.velocity.events.AuthMeVelocityLoginEvent;
import fr.xephi.authme.velocity.events.AuthMeVelocityLogoutEvent;
import fr.xephi.authme.velocity.premium.VelocityPremiumVerificationManager;
import fr.xephi.authme.proxy.ProxyMessageSecurity;
import fr.xephi.authme.proxy.ProxyMessageType;
import fr.xephi.authme.proxy.ProxyMessages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.UUID;

final class VelocityProxyBridge {

    static final MinecraftChannelIdentifier AUTHME_CHANNEL = MinecraftChannelIdentifier.create("authme", "main");
    static final ChannelIdentifier AUTHME_LEGACY_CHANNEL = new LegacyChannelIdentifier("authme:main");

    private static final String PROXY_IDENTITY = "velocity";
    private static final int MAX_RETRIES = 3;

    private final ProxyServer proxyServer;
    private final Logger logger;
    private volatile VelocityProxyConfiguration configuration;
    private final VelocityAuthenticationStore authenticationStore;
    private final Map<String, AtomicInteger> pendingAutoLogins = new ConcurrentHashMap<>();
    private final Set<String> notifiedAuthServers = ConcurrentHashMap.newKeySet();
    private final Set<String> handshakeRetriesPending = ConcurrentHashMap.newKeySet();
    private final PremiumNames premiumNames;
    // Players with a pending premium verification (ran /premium but not yet confirmed via reconnect)
    private final Set<String> pendingPremiumUsernames = ConcurrentHashMap.newKeySet();
    private final VelocityPremiumVerificationManager premiumVerificationManager;
    private final ScheduledExecutorService retryScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "authme-velocity-retry");
        t.setDaemon(true);
        return t;
    });

    VelocityProxyBridge(ProxyServer proxyServer, Logger logger, VelocityProxyConfiguration configuration,
                        VelocityAuthenticationStore authenticationStore, Path dataDirectory) {
        this.proxyServer = proxyServer;
        this.logger = logger;
        this.configuration = configuration;
        this.authenticationStore = authenticationStore;
        this.premiumNames = new PremiumNames(dataDirectory, logger, retryScheduler);
        this.premiumVerificationManager =
            new VelocityPremiumVerificationManager(logger,
                this::requiresPremiumVerification, this::isPendingPremiumVerification,
                this::clearPendingPremiumVerification,
                () -> this.configuration.keepOfflineUuidCompatibility(),
                name -> proxyServer.getPlayer(name).isPresent());
    }

    void reload(VelocityProxyConfiguration configuration) {
        this.configuration = configuration;
        logger.info("Configuration reloaded");
    }

    void registerChannels() {
        proxyServer.getChannelRegistrar().register(AUTHME_CHANNEL, AUTHME_LEGACY_CHANNEL);
        logger.info("Registered AuthMe Velocity bridge channel");
        broadcastProxyStartedHandshake();
        premiumVerificationManager.register();
    }

    private boolean requiresPremiumVerification(String normalizedName) {
        return premiumNames.contains(normalizedName) || isPendingPremiumVerification(normalizedName);
    }

    private boolean isPendingPremiumVerification(String normalizedName) {
        return pendingPremiumUsernames.contains(normalizedName);
    }

    private void clearPendingPremiumVerification(String normalizedName) {
        if (pendingPremiumUsernames.remove(normalizedName)) {
            premiumVerificationManager.clearVerifiedPremium(normalizedName);
            logger.warn("Cleared pending premium verification for '{}' after a failed proxy-side premium handshake",
                normalizedName);
        }
    }

    private void broadcastProxyStartedHandshake() {
        byte[] payload = ProxyMessages.proxyStarted(PROXY_IDENTITY);
        int notified = 0;
        int deferred = 0;
        for (RegisteredServer server : proxyServer.getAllServers()) {
            if (!configuration.isAuthServer(server)) {
                continue;
            }
            String serverName = server.getServerInfo().getName();
            if (server.sendPluginMessage(AUTHME_CHANNEL, payload)) {
                notifiedAuthServers.add(serverName);
                notified++;
                logger.info("Sent proxy.started handshake to auth server '{}'", serverName);
            } else {
                deferred++;
                logger.info("Proxy.started handshake for '{}' could not be sent yet; retrying in background",
                    serverName);
                scheduleHandshakeRetry(server);
            }
        }
        if (notified > 0 || deferred > 0) {
            logger.info("Proxy startup handshake: {} auth server(s) notified, {} pending background retry",
                notified, deferred);
        }
    }

    private void scheduleHandshakeRetry(RegisteredServer server) {
        String serverName = server.getServerInfo().getName();
        if (!handshakeRetriesPending.add(serverName)) {
            return;
        }
        retryScheduler.schedule(() -> {
            handshakeRetriesPending.remove(serverName);
            if (!notifiedAuthServers.contains(serverName)) {
                sendProxyStartedHandshakeIfPending(server, null);
            }
        }, 1, TimeUnit.SECONDS);
    }


    void logConfigurationDetails() {
        if (configuration.allServersAreAuthServers()) {
            logger.info("All registered backend servers are treated as auth servers");
        } else if (configuration.authServers().isEmpty()) {
            logger.warn("No auth servers are configured; autoLogin will only work after authServers is populated"
                + " or allServersAreAuthServers is enabled");
        } else {
            logger.info("Current auth servers:");
            configuration.authServers().forEach(serverName -> logger.info("> {}", serverName));
        }

        if (!configuration.autoLoginEnabled()) {
            logger.info("autoLogin is disabled");
        }

        logger.info("premium.keepOfflineUuidCompatibility is {}",
            configuration.keepOfflineUuidCompatibility() ? "enabled" : "disabled");

        if (configuration.sendOnLogoutEnabled() && configuration.sendOnLogoutTarget().isEmpty()) {
            logger.warn("sendOnLogout is enabled but unloggedUserServer is empty; logout redirects will be skipped");
        }
    }

    void onPluginMessage(PluginMessageEvent event) {
        if (!event.getResult().isAllowed()) {
            return;
        }
        final var identifier = event.getIdentifier();
        if (!(identifier.equals(AUTHME_CHANNEL) || identifier.equals(AUTHME_LEGACY_CHANNEL))) {
            return;
        }
        if (!(event.getSource() instanceof ServerConnection serverConnection)) {
            event.setResult(PluginMessageEvent.ForwardResult.handled());
            logger.debug("Blocked authme:main message from non-server source (client-side spoofing attempt?)");
            return;
        }

        event.setResult(PluginMessageEvent.ForwardResult.handled());

        ParsedMessage parsedMessage = parseMessage(event.getData());
        if (parsedMessage.type() == null || parsedMessage.playerName() == null) {
            return;
        }
        dispatchPluginMessage(parsedMessage, serverConnection);
    }

    private void dispatchPluginMessage(ParsedMessage message, ServerConnection serverConnection) {
        String playerName = message.playerName();
        String serverName = serverConnection.getServer().getServerInfo().getName();

        switch (message.type()) {
            case LOGIN -> handleLoginMessage(playerName, serverName, serverConnection);
            case LOGOUT -> handleLogoutMessage(playerName, serverName);
            case PERFORM_LOGIN_ACK -> handlePerformLoginAck(playerName, serverName);
            default -> handlePremiumMessage(message.type(), playerName);
        }
    }

    private void handleLoginMessage(String playerName, String serverName, ServerConnection serverConnection) {
        if (configuration.isAuthServer(serverConnection.getServer())) {
            logger.info("Player {} authenticated on auth server '{}'", playerName, serverName);

            proxyServer.getPlayer(playerName).ifPresent(player -> proxyServer.getEventManager().fireAndForget(
                new AuthMeVelocityLoginEvent(player, requiresPremiumVerification(playerName))));

            authenticationStore.markAuthenticated(playerName);
            sendAutoLoginIfAlreadySwitched(playerName, serverConnection.getServer());
            redirectPlayer(playerName, configuration.loginServer(), "authentication", false);
        } else if (pendingAutoLogins.remove(playerName) != null) {
            // Implicit ACK: login from non-auth server confirms perform.login was processed
            logger.info("Auto-login confirmed for {} via login from server '{}'", playerName, serverName);
        } else {
            logger.debug("Ignoring login from non-auth server '{}' for {} (no pending auto-login)",
                serverName, playerName);
        }
    }

    private void handleLogoutMessage(String playerName, String serverName) {
        logger.info("Player {} logged out (notified by server '{}')", playerName, serverName);
        authenticationStore.markLoggedOut(playerName);
        proxyServer.getPlayer(playerName).ifPresent(player ->
            proxyServer.getEventManager().fireAndForget(new AuthMeVelocityLogoutEvent(player)));
        if (configuration.sendOnLogoutEnabled()) {
            redirectPlayer(playerName, configuration.sendOnLogoutTarget(), "logout", true);
        }
    }

    private void handlePerformLoginAck(String playerName, String serverName) {
        logger.info("Auto-login ACK received for {} from server '{}'", playerName, serverName);
        pendingAutoLogins.remove(playerName);
        if (configuration.autoLoginEnabled()) {
            // The backend completed the auto-login; without this the proxy keeps denying
            // chat and commands even though the player is authenticated on the backend.
            authenticationStore.markAuthenticated(playerName);
        }
    }

    private void handlePremiumMessage(ProxyMessageType type, String playerName) {
        if (type == ProxyMessageType.PREMIUM_SET) {
            premiumNames.add(playerName);
            pendingPremiumUsernames.remove(playerName);
            logger.debug("Premium enabled for '{}' (proxy cache updated)", playerName);
        } else if (type == ProxyMessageType.PREMIUM_UNSET) {
            premiumNames.remove(playerName);
            pendingPremiumUsernames.remove(playerName);
            premiumVerificationManager.clearVerifiedPremium(playerName);
            logger.debug("Premium disabled for '{}' (proxy cache updated)", playerName);
        } else if (type == ProxyMessageType.PREMIUM_PENDING_SET) {
            pendingPremiumUsernames.add(playerName);
            premiumVerificationManager.clearVerifiedPremium(playerName);
            logger.debug("Pending premium verification started for '{}'", playerName);
        } else if (type == ProxyMessageType.PREMIUM_LIST) {
            premiumNames.replaceFromCsv(playerName);
        } else if (type == ProxyMessageType.PREMIUM_LIST_CHUNK) {
            premiumNames.handleChunk(playerName);
        }
    }

    /**
     * Sends the auto-login {@code perform.login} as soon as an already-authenticated player is about to enter
     * the configuration phase during a server switch, before the backend decides whether to show its blocking
     * pre-join login dialog. The regular {@link #onServerConnected(ServerConnectedEvent)} path fires only after
     * the configuration phase completes — too late to suppress that dialog.
     * <p>
     * During a switch the player is in the "null window" (neither the old nor the new server is set as the
     * current one), so {@link PlayerEnteredConfigurationEvent} cannot reach the target backend. This event
     * fires earlier with the in-flight target connection.
     * <p>
     * This is sent to all servers (not just auth servers) so that a non-auth server running AuthMe
     * also receives the auto-login signal early enough to suppress its login dialog.
     */
    void onPlayerEnterConfiguration(PlayerEnterConfigurationEvent event) {
        sendConfigPhaseAutoLogin(event.server(), event.player().getUsername(), false);
    }

    /**
     * Sends the auto-login {@code perform.login} as soon as an already-authenticated player enters the
     * configuration phase on an auth server, before the backend decides whether to show its blocking
     * pre-join login dialog. The regular {@link #onServerConnected(ServerConnectedEvent)} path fires only
     * after the configuration phase completes — too late to suppress that dialog on a server switch.
     */
    void onPlayerEnteredConfiguration(PlayerEnteredConfigurationEvent event) {
        sendConfigPhaseAutoLogin(event.server(), event.player().getUsername(), true);
    }

    private void sendConfigPhaseAutoLogin(ServerConnection server, String playerName, boolean authServerOnly) {
        if (!configuration.autoLoginEnabled() || server == null) {
            return;
        }
        String normalizedName = VelocityAuthenticationStore.normalizeName(playerName);
        UUID verifiedPremiumUuid = premiumVerificationManager.getVerifiedPremiumUuid(normalizedName);
        boolean isPremiumJoin = verifiedPremiumUuid != null;
        // The pre-join dialog only appears on backends running AuthMe, i.e. auth servers, but a
        // proxy-verified premium player must carry the premium UUID to any backend they enter:
        // with keepOfflineUuidCompatibility the backend sees the offline UUID and needs that
        // UUID in perform.login to finalize the pending premium enrollment.
        if (authServerOnly && !configuration.isAuthServer(server.getServer()) && !isPremiumJoin) {
            return;
        }
        if (!authenticationStore.isAuthenticated(normalizedName) && !isPremiumJoin) {
            return;
        }
        String serverName = server.getServer().getServerInfo().getName();
        boolean sent = sendPerformLoginMessage(server, normalizedName, verifiedPremiumUuid);
        if (sent) {
            logger.info("Sent config-phase auto-login to {}server '{}' for player {} (verifiedPremiumUuid={})",
                authServerOnly ? "auth " : "", serverName, normalizedName, verifiedPremiumUuid);
            initiatePendingLogin(normalizedName);
        } else {
            logger.debug("Config-phase auto-login send to '{}' for {} returned false; onServerConnected will retry",
                serverName, normalizedName);
        }
    }

    void onServerConnected(ServerConnectedEvent event) {
        Player player = event.getPlayer();
        String playerName = player.getUsername();
        String newServer = event.getServer().getServerInfo().getName();
        String prevServer = event.getPreviousServer()
            .map(s -> s.getServerInfo().getName()).orElse("(none)");
        logger.debug("ServerConnected: {} moved from '{}' to '{}'", playerName, prevServer, newServer);

        sendProxyStartedHandshakeIfPending(event.getServer(), player.getCurrentServer().orElse(null));

        if (!configuration.autoLoginEnabled()) {
            logger.debug("autoLogin is disabled, skipping auto-login for {}", playerName);
            return;
        }
        sendAutoLoginOnConnect(player, event.getServer(), playerName, newServer);
    }

    private void sendAutoLoginOnConnect(Player player, RegisteredServer server, String playerName, String newServer) {
        boolean connectingToAuthServer = configuration.isAuthServer(server);

        String normalizedName = VelocityAuthenticationStore.normalizeName(playerName);
        UUID verifiedPremiumUuid = premiumVerificationManager.getVerifiedPremiumUuid(normalizedName);
        logger.info("onServerConnected: {} verifiedPremiumUuid={} connectingToAuthServer={}",
            normalizedName, verifiedPremiumUuid, connectingToAuthServer);

        boolean isPremiumJoin = verifiedPremiumUuid != null;
        if (!authenticationStore.isAuthenticated(normalizedName) && !isPremiumJoin) {
            logger.debug("Skipping auto-login for {} — not authenticated or proxy-verified premium", normalizedName);
            return;
        }
        if (isPremiumJoin) {
            logger.debug("Proxy-verified premium player {} joining '{}' — sending perform.login with premium UUID {}",
                normalizedName, newServer, verifiedPremiumUuid);
        }

        Optional<ServerConnection> currentServer = player.getCurrentServer();
        if (currentServer.isEmpty()) {
            // Velocity hasn't registered the new connection yet; let the retry mechanism handle it
            logger.debug("Player {} has no active server connection in ServerConnectedEvent; scheduling auto-login retry", normalizedName);
            initiatePendingLogin(normalizedName);
            return;
        }

        ServerConnection connection = currentServer.get();
        String serverName = connection.getServer().getServerInfo().getName();
        boolean sent = sendPerformLoginMessage(connection, normalizedName, verifiedPremiumUuid);
        if (sent) {
            logger.info("Sending auto-login request to server '{}' for player {} (verifiedPremiumUuid={})",
                serverName, normalizedName, verifiedPremiumUuid);
        } else {
            logger.warn("Failed to send auto-login request to server '{}' for player {}; scheduling retry", serverName, normalizedName);
        }
        initiatePendingLogin(normalizedName);
    }

    void onPreLogin(PreLoginEvent event) {
        premiumVerificationManager.onPreLogin(event);
    }

    void onGameProfileRequest(GameProfileRequestEvent event) {
        premiumVerificationManager.onGameProfileRequest(event);
    }

    void onServerPreConnect(ServerPreConnectEvent event) {
        if (!configuration.serverSwitchRequiresAuth()) {
            return;
        }

        Player player = event.getPlayer();
        if (authenticationStore.isAuthenticated(player)) {
            return;
        }
        Optional<RegisteredServer> target = event.getResult().getServer();
        if (target.isEmpty() || configuration.isAuthServer(target.get())) {
            return;
        }

        String targetName = target.get().getServerInfo().getName();
        logger.debug("Blocking unauthenticated player {} from switching to server '{}'",
            player.getUsername(), targetName);

        event.setResult(ServerPreConnectEvent.ServerResult.denied());
        Component message = Component.text(configuration.serverSwitchKickMessage(), NamedTextColor.RED);
        if (player.getCurrentServer().isPresent()) {
            player.sendMessage(message);
        } else {
            player.disconnect(message);
        }
    }

    void onCommandExecute(CommandExecuteEvent event) {
        if (!configuration.commandsRequireAuth()) {
            return;
        }
        if (!(event.getCommandSource() instanceof Player player)) {
            return;
        }
        if (!isUnauthenticatedOnAuthServer(player) || configuration.isWhitelistedCommand(event.getCommand())) {
            return;
        }
        logger.debug("Blocking command '{}' from unauthenticated player {}", event.getCommand(), player.getUsername());
        event.setResult(CommandExecuteEvent.CommandResult.denied());
    }

    void onPlayerChat(PlayerChatEvent event) {
        if (!configuration.chatRequiresAuth()) {
            return;
        }
        Player player = event.getPlayer();
        if (isUnauthenticatedOnAuthServer(player)) {
            logger.debug("Blocking chat from unauthenticated player {}", player.getUsername());
            event.setResult(PlayerChatEvent.ChatResult.denied());
        }
    }

    private boolean isUnauthenticatedOnAuthServer(Player player) {
        Optional<ServerConnection> server = player.getCurrentServer();
        return server.isPresent()
            && configuration.isAuthServer(server.get().getServer())
            && !authenticationStore.isAuthenticated(player);
    }

    void onDisconnect(DisconnectEvent event) {
        String normalizedName = VelocityAuthenticationStore.normalizeName(event.getPlayer().getUsername());
        Optional<Player> connectedPlayer = proxyServer.getPlayer(event.getPlayer().getUsername());
        if (connectedPlayer.isPresent() && connectedPlayer.get() != event.getPlayer()) {
            logger.debug("Keeping auth state for {}: a duplicate connection was closed", normalizedName);
            return;
        }
        if (pendingAutoLogins.remove(normalizedName) != null) {
            logger.debug("Cancelling pending auto-login for {} (player disconnected)", normalizedName);
        }
        if (authenticationStore.isAuthenticated(normalizedName)) {
            logger.debug("Clearing auth state for {} (player disconnected)", normalizedName);
        }
        authenticationStore.markLoggedOut(event.getPlayer().getUsername());
        premiumVerificationManager.clearVerifiedPremium(normalizedName);
    }

    void shutdown() {
        logger.info("Shutting down retry scheduler");
        premiumVerificationManager.shutdown();
        retryScheduler.shutdownNow();
    }

    private void sendAutoLoginIfAlreadySwitched(String normalizedName, RegisteredServer authServer) {
        if (!configuration.autoLoginEnabled()) {
            return;
        }
        Optional<Player> playerOpt = proxyServer.getPlayer(normalizedName);
        if (playerOpt.isEmpty()) {
            return;
        }
        Optional<ServerConnection> currentConn = playerOpt.get().getCurrentServer();
        if (currentConn.isEmpty()) {
            return;
        }
        RegisteredServer currentServer = currentConn.get().getServer();
        if (currentServer.equals(authServer)) {
            // Still on auth server — normal flow, ServerConnectedEvent will handle it on switch
            return;
        }
        // Race condition: player already switched before login message arrived at the proxy
        String currentServerName = currentServer.getServerInfo().getName();
        logger.info("Player {} already on server '{}' when login message arrived — sending auto-login immediately",
            normalizedName, currentServerName);
        UUID verifiedPremiumUuid = premiumVerificationManager.getVerifiedPremiumUuid(normalizedName);
        boolean sent = sendPerformLoginMessage(currentConn.get(), normalizedName, verifiedPremiumUuid);
        if (sent) {
            initiatePendingLogin(normalizedName);
        } else {
            logger.warn("Failed to send auto-login to '{}' for {} (race condition path)", currentServerName, normalizedName);
        }
    }

    private void sendProxyStartedHandshakeIfPending(RegisteredServer server, ServerConnection triggeringConnection) {
        if (!configuration.isAuthServer(server)) {
            return;
        }
        String serverName = server.getServerInfo().getName();
        if (!notifiedAuthServers.add(serverName)) {
            return;
        }
        byte[] payload = ProxyMessages.proxyStarted(PROXY_IDENTITY);
        // Try via the specific triggering connection first to avoid the race condition where
        // ServerConnectedEvent fires before the player is added to RegisteredServer.playersConnected.
        boolean sent = triggeringConnection != null
            && triggeringConnection.sendPluginMessage(AUTHME_CHANNEL, payload);
        if (!sent) {
            sent = server.sendPluginMessage(AUTHME_CHANNEL, payload);
        }
        if (sent) {
            logger.info("Sent deferred proxy.started handshake to auth server '{}'", serverName);
        } else {
            notifiedAuthServers.remove(serverName);
            // Velocity cannot send to a backend with no player connected, but the retry must not
            // depend on a player event: a player kicked before joining would leave the premium
            // list unsynced forever. Keep retrying in the background until the send succeeds.
            logger.info("Failed to send deferred proxy.started handshake to '{}'; scheduling retry", serverName);
            scheduleHandshakeRetry(server);
        }
    }

    private void initiatePendingLogin(String normalizedName) {
        AtomicInteger attempts = new AtomicInteger(0);
        pendingAutoLogins.put(normalizedName, attempts);
        scheduleRetry(normalizedName, attempts);
    }

    private void scheduleRetry(String normalizedName, AtomicInteger attempts) {
        retryScheduler.schedule(() -> {
            if (pendingAutoLogins.get(normalizedName) != attempts) {
                return;
            }
            Optional<Player> playerOpt = proxyServer.getPlayer(normalizedName);
            if (playerOpt.isEmpty()) {
                pendingAutoLogins.remove(normalizedName);
                logger.debug("Auto-login retry cancelled for {} (player no longer online)", normalizedName);
                return;
            }
            Optional<ServerConnection> serverOpt = playerOpt.get().getCurrentServer();
            int attempt = attempts.getAndIncrement();
            if (attempt >= MAX_RETRIES) {
                pendingAutoLogins.remove(normalizedName);
                if (serverOpt.isEmpty()) {
                    logger.warn("No server connection for {} after {} deferred retries; giving up on auto-login",
                        normalizedName, MAX_RETRIES);
                } else {
                    logger.warn("No auto-login ACK received for {} after {} retries; giving up", normalizedName, MAX_RETRIES);
                }
                return;
            }
            if (serverOpt.isEmpty()) {
                logger.debug("Auto-login retry for {} deferred: no active server connection yet", normalizedName);
                scheduleRetry(normalizedName, attempts);
                return;
            }
            String serverName = serverOpt.get().getServer().getServerInfo().getName();
            logger.debug("Retrying auto-login for {} on server '{}' (attempt {}/{})",
                normalizedName, serverName, attempt + 1, MAX_RETRIES);

            UUID verifiedPremiumUuid = premiumVerificationManager.getVerifiedPremiumUuid(normalizedName);
            sendPerformLoginMessage(serverOpt.get(), normalizedName, verifiedPremiumUuid);
            scheduleRetry(normalizedName, attempts);
        }, 1, TimeUnit.SECONDS);
    }

    private ParsedMessage parseMessage(byte[] data) {
        ByteArrayDataInput input = ByteStreams.newDataInput(data);
        try {
            String typeId = input.readUTF();
            ProxyMessageType type = ProxyMessageType.fromId(typeId);
            if (type == null || type == ProxyMessageType.PERFORM_LOGIN || type == ProxyMessageType.PROXY_STARTED) {
                logger.debug("Ignoring unknown authme:main message type '{}'", typeId);
                return new ParsedMessage(null, null);
            }
            // premium.list and premium.list.chunk carry non-player-name data; read as-is
            String argument = input.readUTF();
            boolean rawArgument = type == ProxyMessageType.PREMIUM_LIST || type == ProxyMessageType.PREMIUM_LIST_CHUNK;
            if (!rawArgument && argument.isEmpty()) {
                logger.warn("Ignoring authme:main message '{}' with an empty player name", typeId);
                return new ParsedMessage(null, null);
            }
            return new ParsedMessage(type, rawArgument ? argument : VelocityAuthenticationStore.normalizeName(argument));
        } catch (IllegalStateException e) {
            logger.warn("Received malformed AuthMe plugin message on the authme:main channel");
            return new ParsedMessage(null, null);
        }
    }

    private boolean sendPerformLoginMessage(ServerConnection server, String normalizedName, UUID verifiedPremiumUuid) {
        long timestamp = System.currentTimeMillis();
        String hmac = ProxyMessageSecurity.computeHmac(
            configuration.sharedSecret(), normalizedName, timestamp, verifiedPremiumUuid);
        byte[] payload = ProxyMessages.performLogin(ProxyMessageType.PERFORM_LOGIN.id(),
            normalizedName, timestamp, verifiedPremiumUuid, hmac);
        return server.sendPluginMessage(AUTHME_CHANNEL, payload);
    }

    /** Shared redirect path for the post-login and post-logout server transfers. */
    private void redirectPlayer(String normalizedPlayerName, String target, String reason, boolean warnIfNoTarget) {
        if (target.isEmpty()) {
            if (warnIfNoTarget) {
                logger.warn("Received {} for {} but no target server is configured", reason, normalizedPlayerName);
            }
            return;
        }
        Optional<Player> playerOpt = proxyServer.getPlayer(normalizedPlayerName);
        if (playerOpt.isEmpty()) {
            logger.debug("Cannot redirect {} to '{}': player no longer on proxy", normalizedPlayerName, target);
            return;
        }
        Optional<RegisteredServer> targetServer = proxyServer.getServer(target);
        if (targetServer.isEmpty()) {
            logger.warn("Target server '{}' is not registered on the proxy; cannot redirect {} after {}",
                target, normalizedPlayerName, reason);
            return;
        }
        logger.info("Redirecting {} to server '{}' after {}", normalizedPlayerName, target, reason);
        playerOpt.get().createConnectionRequest(targetServer.get()).fireAndForget();
    }

    private record ParsedMessage(ProxyMessageType type, String playerName) {
    }
}
