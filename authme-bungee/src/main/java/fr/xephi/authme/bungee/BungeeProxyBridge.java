package fr.xephi.authme.bungee;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import fr.xephi.authme.bungee.events.AuthMeBungeeLoginEvent;
import fr.xephi.authme.bungee.events.AuthMeBungeeLogoutEvent;
import fr.xephi.authme.bungee.premium.BungeePremiumOnlineModeHandler;
import fr.xephi.authme.bungee.premium.PremiumVerificationManager;
import fr.xephi.authme.bungee.premium.PremiumVerificationManagers;
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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
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

    private final ProxyServer proxyServer;
    private final Logger logger;
    private BungeeProxyConfiguration configuration;
    private final BungeeAuthenticationStore authenticationStore;
    private final Map<String, AtomicInteger> pendingAutoLogins = new ConcurrentHashMap<>();
    private final Set<String> notifiedAuthServers = ConcurrentHashMap.newKeySet();
    private volatile Set<String> premiumUsernames = ConcurrentHashMap.newKeySet();
    private List<String> premiumListBuffer = new ArrayList<>();
    private int expectedPremiumListChunk = -1;
    // Players with a pending premium verification (ran /premium but not yet confirmed via reconnect)
    private volatile Set<String> pendingPremiumUsernames = ConcurrentHashMap.newKeySet();
    private final BungeePremiumOnlineModeHandler premiumOnlineModeHandler;
    private final PremiumVerificationManager premiumVerificationManager;
    private final ScheduledExecutorService retryScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "authme-bungee-retry");
        t.setDaemon(true);
        return t;
    });
    private final Path cacheFile;

    BungeeProxyBridge(ProxyServer proxyServer, Logger logger, BungeeProxyConfiguration configuration,
                      BungeeAuthenticationStore authenticationStore, Path dataDirectory) {
        this.proxyServer = proxyServer;
        this.logger = logger;
        this.configuration = configuration;
        this.authenticationStore = authenticationStore;
        this.premiumOnlineModeHandler = new BungeePremiumOnlineModeHandler(this::requiresPremiumVerification);
        this.premiumVerificationManager =
            PremiumVerificationManagers.create(proxyServer, logger,
                this::requiresPremiumVerification, this::isPendingPremiumVerification,
                this::clearPendingPremiumVerification,
                () -> this.configuration.keepOfflineUuidCompatibility());
        this.cacheFile = dataDirectory != null ? dataDirectory.resolve("premium_names.cache") : null;
        if (cacheFile != null) {
            loadPremiumNamesCache();
        }
    }

    private void loadPremiumNamesCache() {
        Set<String> loaded = ConcurrentHashMap.newKeySet();
        try (BufferedReader reader = Files.newBufferedReader(cacheFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String name = line.trim();
                if (!name.isEmpty()) {
                    loaded.add(name);
                }
            }
            premiumUsernames = loaded;
            logger.info("Loaded " + loaded.size() + " premium username(s) from cache");
        } catch (NoSuchFileException ignored) {
            // No cache yet — first startup
        } catch (IOException e) {
            logger.warning("Failed to load premium names cache: " + e.getMessage());
        }
    }

    private void savePremiumNamesAsync() {
        if (cacheFile == null) {
            return;
        }
        Set<String> snapshot = new HashSet<>(premiumUsernames);
        retryScheduler.execute(() -> {
            Path tmp = cacheFile.resolveSibling(cacheFile.getFileName() + ".tmp");
            try (BufferedWriter writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                for (String name : snapshot) {
                    writer.write(name);
                    writer.newLine();
                }
                Files.move(tmp, cacheFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                logger.warning("Failed to save premium names cache: " + e.getMessage());
            }
        });
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
        return premiumUsernames.contains(normalizedName) || pendingPremiumUsernames.contains(normalizedName);
    }

    private boolean isPendingPremiumVerification(String normalizedName) {
        return pendingPremiumUsernames.contains(normalizedName);
    }

    private void clearPendingPremiumVerification(String normalizedName) {
        if (pendingPremiumUsernames.remove(normalizedName)) {
            premiumVerificationManager.clearVerifiedPremium(normalizedName);
            logger.warning("Cleared pending premium verification for '" + normalizedName
                + "' after a failed proxy-side premium handshake");
        }
    }

    void broadcastProxyStartedHandshake() {
        byte[] payload = createProxyStartedMessage();
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

    private byte[] createProxyStartedMessage() {
        ByteArrayDataOutput output = ByteStreams.newDataOutput();
        output.writeUTF(ProxyMessageType.PROXY_STARTED.id());
        output.writeUTF(PROXY_IDENTITY);
        return output.toByteArray();
    }

    @EventHandler
    public void onPluginMessage(PluginMessageEvent event) {
        if (event.isCancelled() || !AUTHME_CHANNEL.equals(event.getTag())) {
            return;
        }
        if (!(event.getSender() instanceof Server server)) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(true);

        ParsedPluginMessage parsedMessage = parsePluginMessage(event.getData());
        if (parsedMessage.type() == null || parsedMessage.playerName() == null) {
            return;
        }

        // Name-bearing messages are already normalized by parsePluginMessage; the premium list
        // messages carry a raw comma-separated payload that each handler normalizes itself.
        String playerName = parsedMessage.playerName();

        switch (parsedMessage.type()) {
            case LOGIN -> {
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
                    redirectToLoginServer(playerName);
                } else if (pendingAutoLogins.containsKey(playerName)) {
                    // Implicit ACK: login from non-auth server confirms perform.login was processed
                    logger.info("Auto-login confirmed for " + playerName
                        + " via login from server '" + server.getInfo().getName() + "'");
                    cancelPendingLogin(playerName);
                }
            }
            case LOGOUT -> {
                authenticationStore.markLoggedOut(playerName);
                ProxiedPlayer player = proxyServer.getPlayer(playerName);
                if (player != null) {
                    proxyServer.getPluginManager().callEvent(new AuthMeBungeeLogoutEvent(player));
                }

                redirectLoggedOutPlayer(playerName);
            }
            case PERFORM_LOGIN_ACK -> {
                logger.info("Auto-login ACK received for " + playerName
                    + " from server '" + server.getInfo().getName() + "'");
                cancelPendingLogin(playerName);
            }
            case PREMIUM_SET -> {
                premiumUsernames.add(playerName);
                pendingPremiumUsernames.remove(playerName);
                logger.fine(() -> "Premium enabled for '" + playerName + "' (proxy cache updated)");
                savePremiumNamesAsync();
            }
            case PREMIUM_UNSET -> {
                premiumUsernames.remove(playerName);
                pendingPremiumUsernames.remove(playerName);
                premiumVerificationManager.clearVerifiedPremium(playerName);
                logger.fine(() -> "Premium disabled for '" + playerName + "' (proxy cache updated)");
                savePremiumNamesAsync();
            }
            case PREMIUM_PENDING_SET -> {
                pendingPremiumUsernames.add(playerName);
                premiumVerificationManager.clearVerifiedPremium(playerName);
                logger.fine(() -> "Pending premium verification started for '" + playerName + "'");
            }
            case PREMIUM_LIST -> {
                Set<String> newPremiumSet = ConcurrentHashMap.newKeySet();
                addNormalizedPremiumNames(newPremiumSet, playerName);
                replacePremiumNames(newPremiumSet);
            }
            case PREMIUM_LIST_CHUNK -> handlePremiumListChunk(playerName);
            default -> {
                // Outbound-only message types are never handled on the inbound channel.
            }
        }
    }

    private synchronized void handlePremiumListChunk(String payload) {
        String[] parts = payload.split(":", 3);
        int sequence;
        try {
            sequence = parts.length < 3 ? -1 : Integer.parseInt(parts[0]);
        } catch (NumberFormatException e) {
            sequence = -1;
        }
        if (sequence < 0) {
            logger.warning("Malformed premium.list.chunk payload: " + payload);
            return;
        }
        if (sequence == 0) {
            premiumListBuffer = new ArrayList<>();
            expectedPremiumListChunk = 0;
        }
        if (sequence != expectedPremiumListChunk) {
            logger.warning("Discarding premium list: received chunk " + sequence + " instead of "
                + expectedPremiumListChunk);
            premiumListBuffer = new ArrayList<>();
            expectedPremiumListChunk = -1;
            return;
        }
        ++expectedPremiumListChunk;
        addNormalizedPremiumNames(premiumListBuffer, parts[2]);
        if ("1".equals(parts[1])) {
            Set<String> newPremiumSet = ConcurrentHashMap.newKeySet();
            newPremiumSet.addAll(premiumListBuffer);
            premiumListBuffer = new ArrayList<>();
            expectedPremiumListChunk = -1;
            replacePremiumNames(newPremiumSet);
        }
    }

    private void replacePremiumNames(Set<String> names) {
        premiumUsernames = names;
        logger.info("Premium list received from backend: " + premiumUsernames.size() + " premium player(s)");
        savePremiumNamesAsync();
    }

    private static void addNormalizedPremiumNames(Collection<String> target, String csv) {
        if (csv.isEmpty()) {
            return;
        }
        for (String name : csv.split(",")) {
            if (!name.isEmpty()) {
                target.add(BungeeAuthenticationStore.normalizeName(name.trim()));
            }
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
        UUID verifiedPremiumUuid = premiumVerificationManager.getVerifiedPremiumUuid(normalizedName);
        boolean isPremiumJoin = connectingToAuthServer && verifiedPremiumUuid != null;
        if (!authenticationStore.isAuthenticated(player) && !isPremiumJoin) {
            return;
        }
        if (isPremiumJoin) {
            logger.fine("PacketEvents-verified premium player " + normalizedName
                + " joining auth server — sending perform.login immediately");
        }
        String serverName = currentServer.getInfo().getName();
        logger.info("Sending auto-login request to server '" + serverName + "' for player " + normalizedName);
        currentServer.getInfo().sendData(
            AUTHME_CHANNEL, createPerformLoginMessage(normalizedName, verifiedPremiumUuid), false);
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
        if (!isUnauthenticatedOnAuthServer(player)) {
            return false;
        }
        return !command || !configuration.isWhitelistedCommand(event.getMessage());
    }

    private boolean isUnauthenticatedOnAuthServer(ProxiedPlayer player) {
        return !authenticationStore.isAuthenticated(player)
            && player.getServer() != null
            && configuration.isAuthServer(player.getServer().getInfo());
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
        cancelPendingLogin(normalizedName);
        authenticationStore.clear(event.getPlayer());
        premiumVerificationManager.clearVerifiedPremium(normalizedName);
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
        UUID verifiedPremiumUuid = premiumVerificationManager.getVerifiedPremiumUuid(normalizedName);
        currentConn.getInfo().sendData(
            AUTHME_CHANNEL, createPerformLoginMessage(normalizedName, verifiedPremiumUuid), false);
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
            server.sendData(AUTHME_CHANNEL, createProxyStartedMessage(), false);
            logger.info("Sent deferred proxy.started handshake to auth server '" + serverName + "'");
        } else {
            notifiedAuthServers.remove(serverName);
            logger.info("Failed to send deferred proxy.started handshake to '" + serverName + "'; scheduling retry");
            retryScheduler.schedule(() -> sendProxyStartedHandshakeIfPending(server), 1, TimeUnit.SECONDS);
        }
    }

    private void initiatePendingLogin(String normalizedName) {
        pendingAutoLogins.put(normalizedName, new AtomicInteger(0));
        scheduleRetry(normalizedName);
    }

    private void cancelPendingLogin(String normalizedName) {
        pendingAutoLogins.remove(normalizedName);
    }

    private void scheduleRetry(String normalizedName) {
        retryScheduler.schedule(() -> {
            AtomicInteger attempts = pendingAutoLogins.get(normalizedName);
            if (attempts == null) {
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
            UUID verifiedPremiumUuid = premiumVerificationManager.getVerifiedPremiumUuid(normalizedName);
            server.getInfo().sendData(
                AUTHME_CHANNEL, createPerformLoginMessage(normalizedName, verifiedPremiumUuid), false);
            scheduleRetry(normalizedName);
        }, 1, TimeUnit.SECONDS);
    }

    private ParsedPluginMessage parsePluginMessage(byte[] data) {
        ByteArrayDataInput input = ByteStreams.newDataInput(data);

        try {
            ProxyMessageType type = ProxyMessageType.fromId(input.readUTF());
            if (type == null || type == ProxyMessageType.PERFORM_LOGIN || type == ProxyMessageType.PROXY_STARTED) {
                return ParsedPluginMessage.ignored();
            }
            String argument = input.readUTF();
            boolean rawArgument = type == ProxyMessageType.PREMIUM_LIST || type == ProxyMessageType.PREMIUM_LIST_CHUNK;
            return new ParsedPluginMessage(type, rawArgument ? argument : BungeeAuthenticationStore.normalizeName(argument));
        } catch (IllegalStateException e) {
            logger.warning("Received malformed AuthMe plugin message on the authme:main channel");
            return ParsedPluginMessage.ignored();
        }
    }

    private void redirectToLoginServer(String normalizedPlayerName) {
        if (configuration.loginServer().isEmpty()) {
            return;
        }
        ProxiedPlayer player = proxyServer.getPlayer(normalizedPlayerName);
        if (player == null) {
            logger.fine("Cannot redirect " + normalizedPlayerName + " to loginServer: player no longer on proxy");
            return;
        }
        ServerInfo targetServer = proxyServer.getServerInfo(configuration.loginServer());
        if (targetServer == null) {
            logger.warning("loginServer '" + configuration.loginServer()
                + "' is not registered on the proxy; cannot redirect " + normalizedPlayerName);
            return;
        }
        logger.info("Redirecting " + normalizedPlayerName + " to login server '"
            + configuration.loginServer() + "' after authentication");
        player.connect(targetServer);
    }

    private void redirectLoggedOutPlayer(String normalizedPlayerName) {
        if (!configuration.sendOnLogoutEnabled()) {
            return;
        }
        if (configuration.sendOnLogoutTarget().isEmpty()) {
            logger.warning("Received logout for " + normalizedPlayerName
                + " but sendOnLogout has no configured target server");
            return;
        }

        ProxiedPlayer player = proxyServer.getPlayer(normalizedPlayerName);
        if (player == null) {
            return;
        }

        ServerInfo targetServer = proxyServer.getServerInfo(configuration.sendOnLogoutTarget());
        if (targetServer == null) {
            logger.warning("Received logout for " + normalizedPlayerName + " but target server '"
                + configuration.sendOnLogoutTarget() + "' is not registered on the proxy");
            return;
        }

        player.connect(targetServer);
    }

    private byte[] createPerformLoginMessage(String normalizedName, UUID verifiedPremiumUuid) {
        long timestamp = System.currentTimeMillis();
        String hmac = ProxyMessageSecurity.computeHmac(
            configuration.sharedSecret(), normalizedName, timestamp, verifiedPremiumUuid);
        ByteArrayDataOutput output = ByteStreams.newDataOutput();
        output.writeUTF(ProxyMessageType.PERFORM_LOGIN.id());
        output.writeUTF(normalizedName);
        output.writeLong(timestamp);
        output.writeUTF(verifiedPremiumUuid == null ? "" : verifiedPremiumUuid.toString());
        output.writeUTF(hmac);
        return output.toByteArray();
    }

    private record ParsedPluginMessage(ProxyMessageType type, String playerName) {

        private static ParsedPluginMessage ignored() {
            return new ParsedPluginMessage(null, null);
        }
    }
}
