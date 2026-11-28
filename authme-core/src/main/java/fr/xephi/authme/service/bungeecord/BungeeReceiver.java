package fr.xephi.authme.service.bungeecord;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import fr.xephi.authme.AuthMe;
import fr.xephi.authme.ConsoleLogger;
import fr.xephi.authme.data.ProxySessionManager;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.initialization.SettingsDependent;
import fr.xephi.authme.output.ConsoleLoggerFactory;
import fr.xephi.authme.process.Management;
import fr.xephi.authme.security.HashUtils;
import fr.xephi.authme.service.BukkitService;
import fr.xephi.authme.service.ProxyLoginRequestValidator;
import fr.xephi.authme.settings.Settings;
import fr.xephi.authme.settings.properties.HooksSettings;
import fr.xephi.authme.settings.properties.PremiumSettings;
import fr.xephi.authme.util.UuidUtils;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.Messenger;
import org.bukkit.plugin.messaging.PluginMessageListener;

import javax.inject.Inject;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class BungeeReceiver implements PluginMessageListener, SettingsDependent {

    private final ConsoleLogger logger = ConsoleLoggerFactory.get(BungeeReceiver.class);

    private final AuthMe plugin;
    private final BukkitService bukkitService;
    private final ProxySessionManager proxySessionManager;
    private final Management management;
    private final BungeeSender bungeeSender;
    private final DataSource dataSource;
    private final ProxyLoginRequestValidator proxyLoginRequestValidator;

    private static final long MAX_AGE_MILLIS = 30_000L;
    private static final long REJECTION_LOG_INTERVAL_MILLIS = 1_000L;

    // Avoid cloning values() and wrapping an Optional on every incoming plugin message.
    private static final Map<String, MessageType> MESSAGE_TYPES_BY_ID = createMessageTypeMap();

    private static Map<String, MessageType> createMessageTypeMap() {
        Map<String, MessageType> map = new HashMap<>();
        for (MessageType type : MessageType.values()) {
            map.put(type.getId(), type);
        }
        return Collections.unmodifiableMap(map);
    }

    private volatile boolean isEnabled;
    private volatile boolean premiumEnabled;
    private volatile String proxySharedSecret;
    private boolean channelRegistered;
    // Clients can send on this channel too: at most one premium-list query runs at a time
    private final AtomicBoolean premiumListInFlight = new AtomicBoolean();
    private volatile long lastRejectionLogMillis;

    @Inject
    BungeeReceiver(AuthMe plugin, BukkitService bukkitService, ProxySessionManager proxySessionManager,
                   Management management, BungeeSender bungeeSender, DataSource dataSource,
                   ProxyLoginRequestValidator proxyLoginRequestValidator, Settings settings) {
        this.plugin = plugin;
        this.bukkitService = bukkitService;
        this.proxySessionManager = proxySessionManager;
        this.management = management;
        this.bungeeSender = bungeeSender;
        this.dataSource = dataSource;
        this.proxyLoginRequestValidator = proxyLoginRequestValidator;
        reload(settings);
    }

    @Override
    public void reload(Settings settings) {
        this.proxySharedSecret = settings.getProperty(HooksSettings.PROXY_SHARED_SECRET);
        this.isEnabled = settings.getProperty(HooksSettings.BUNGEECORD);
        this.premiumEnabled = settings.getProperty(PremiumSettings.ENABLE_PREMIUM);
        final Messenger messenger = plugin.getServer().getMessenger();
        if (messenger == null) {
            return;
        }
        // Track our own registration rather than querying the channel: other listeners
        // (e.g. the Paper configuration-phase receiver) may register the same channel.
        if (this.isEnabled && !channelRegistered) {
            messenger.registerIncomingPluginChannel(plugin, MessageType.CHANNEL, this);
            channelRegistered = true;
        } else if (!this.isEnabled && channelRegistered) {
            messenger.unregisterIncomingPluginChannel(plugin, MessageType.CHANNEL, this);
            channelRegistered = false;
        }
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] data) {
        if (!isEnabled || !channel.equals(MessageType.CHANNEL)) {
            return;
        }

        ByteArrayDataInput in = ByteStreams.newDataInput(data);
        String typeId = readField(in, "Received malformed AuthMe plugin message on authme:main");
        if (typeId == null) {
            return;
        }

        MessageType messageType = MESSAGE_TYPES_BY_ID.get(typeId);
        if (messageType == null) {
            logger.debug("Received unsupported AuthMe plugin message type: {0}", typeId);
            return;
        }

        String argument = readField(in, null);
        if (argument == null) {
            warnThrottled("Received invalid AuthMe plugin message of type " + messageType.name()
                + ": argument is missing!");
            return;
        }

        if (messageType == MessageType.PROXY_STARTED) {
            logger.info("Proxy plugin '" + argument + "' has started and registered the authme:main channel");
            if (!premiumListInFlight.compareAndSet(false, true)) {
                return;
            }
            bukkitService.runTaskAsynchronously(() -> {
                // Always send the list, even when it is empty: an empty list is authoritative and
                // lets the proxy replace a stale premium cache. With premium disabled, stored
                // premium names must not reach the proxy, or it keeps verifying those players.
                List<String> premiumNames;
                try {
                    premiumNames = premiumEnabled ? dataSource.getPremiumUsernames() : Collections.<String>emptyList();
                } finally {
                    premiumListInFlight.set(false);
                }
                bukkitService.scheduleSyncTaskFromOptionallyAsyncTask(
                    () -> sendPremiumList(player, argument, premiumNames));
            });
        } else if (messageType == MessageType.PERFORM_LOGIN) {
            VerifiedProxyLogin verified = parseAndVerifyPerformLogin(in, argument);
            if (verified != null) {
                performLogin(verified.name, verified.verifiedPremiumUuid);
            }
        }
    }

    private void sendPremiumList(Player player, String proxyName, List<String> premiumNames) {
        // Re-fetch a carrier at send-time: the original player may have gone offline
        // during the async DB query. Fall back to the triggering player so the list
        // still goes out when no fully-joined player exists yet (first join during
        // the login phase) or the single online player disconnected mid-query.
        Player carrier = bukkitService.getOnlinePlayers().stream().findFirst().orElse(player);
        if (carrier == null) {
            logger.warning("Cannot send premium list to proxy '" + proxyName
                + "': no online player available as carrier.");
            return;
        }
        bungeeSender.sendPremiumList(carrier, premiumNames);
        logger.info("Sent premium list (" + premiumNames.size() + " player(s)) to proxy '" + proxyName + "'");
    }

    /**
     * Reads a UTF field from a plugin message, optionally logging a warning when the field is absent.
     *
     * @param in the data input
     * @param missingFieldWarning warning to log if the field cannot be read, or {@code null} to log nothing
     * @return the field value, or {@code null} if it was missing
     */
    private String readField(ByteArrayDataInput in, String missingFieldWarning) {
        try {
            return in.readUTF();
        } catch (IllegalStateException e) {
            if (missingFieldWarning != null) {
                warnThrottled(missingFieldWarning);
            }
            return null;
        }
    }

    /**
     * Parses and HMAC-verifies the remainder of a {@code perform.login} message (everything after the
     * player-name argument).
     *
     * @param in the data input positioned right after the player-name argument
     * @param playerName the player-name argument that was already read
     * @return the verified login data, or {@code null} if the message was malformed or failed verification
     */
    private VerifiedProxyLogin parseAndVerifyPerformLogin(ByteArrayDataInput in, String playerName) {
        long timestamp;
        String uuidOrHmac;
        UUID premiumUuid;
        try {
            timestamp = in.readLong();
            uuidOrHmac = in.readUTF();
            premiumUuid = UuidUtils.parseUuidSafely(uuidOrHmac);
        } catch (IllegalStateException e) {
            warnThrottled("Received perform.login without HMAC — update your proxy plugin");
            return null;
        }
        String hmac = uuidOrHmac;
        if (premiumUuid != null || uuidOrHmac.isEmpty()) {
            try {
                hmac = in.readUTF();
            } catch (IllegalStateException e) {
                // HMAC was concatenated into uuidOrHmac; keep using it as the HMAC.
            }
        }
        if (!verifyHmac(playerName, timestamp, premiumUuid, hmac)) {
            return null;
        }
        return new VerifiedProxyLogin(playerName, premiumUuid);
    }

    /**
     * Validates and queues a {@code perform.login} received during Paper/Folia's connection
     * configuration phase, when the player does not yet exist as a {@link Player}. Queuing it in the
     * {@link ProxySessionManager} lets the blocking pre-join login dialog be skipped (and
     * {@code processJoin} auto-login the player) instead of waiting for the post-join
     * {@code perform.login}, which arrives only after the configuration phase has completed.
     *
     * @param data the raw plugin-message payload
     * @return the normalized player name if this was a valid {@code perform.login}, otherwise {@code null}
     */
    public String handleConfigPhasePerformLogin(byte[] data) {
        if (!isEnabled) {
            return null;
        }
        ByteArrayDataInput in = ByteStreams.newDataInput(data);
        String typeId = readField(in, null);
        if (typeId == null || !MessageType.PERFORM_LOGIN.getId().equals(typeId)) {
            return null;
        }
        String argument = readField(in, null);
        if (argument == null) {
            return null;
        }
        VerifiedProxyLogin verified = parseAndVerifyPerformLogin(in, argument);
        if (verified == null) {
            return null;
        }
        proxySessionManager.processProxySessionMessage(verified.name, verified.verifiedPremiumUuid);
        logger.debug("Config-phase perform.login validated and queued for {0}", verified.name);
        return verified.name.toLowerCase(Locale.ROOT);
    }

    private boolean verifyHmac(String playerName, long timestamp, UUID verifiedPremiumUuid, String providedHmac) {
        String rejectionReason;
        if (proxySharedSecret.isEmpty()) {
            rejectionReason = "Hooks.proxySharedSecret is not configured — set the same secret on all backend servers and the proxy";
        } else if (Math.abs(System.currentTimeMillis() - timestamp) > MAX_AGE_MILLIS) {
            rejectionReason = "message has expired";
        } else {
            String expectedHmac = HashUtils.hmacSha256(proxySharedSecret,
                playerName + ":" + timestamp + ":" + (verifiedPremiumUuid == null ? "" : verifiedPremiumUuid));
            if (HashUtils.isEqual(expectedHmac, providedHmac)) {
                return true;
            }
            rejectionReason = "invalid HMAC";
        }
        warnThrottled("Rejected perform.login for " + sanitizeForLog(playerName) + ": " + rejectionReason);
        return false;
    }

    /** Any client can send these messages: warn at most once per interval and keep the rest at debug level. */
    private void warnThrottled(String message) {
        long now = System.currentTimeMillis();
        if (now - lastRejectionLogMillis >= REJECTION_LOG_INTERVAL_MILLIS) {
            lastRejectionLogMillis = now;
            logger.warning(message);
        } else {
            logger.debug(message);
        }
    }

    /** Replaces control characters and caps the length of client-supplied text before it is logged. */
    static String sanitizeForLog(String text) {
        int length = Math.min(text.length(), 32);
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            char c = text.charAt(i);
            sb.append(c < 0x20 || c == 0x7f ? '?' : c);
        }
        return sb.toString();
    }

    private void performLogin(String name, UUID verifiedPremiumUuid) {
        logger.debug("Received perform.login request for {0}", name);
        // Always queue in the proxy session manager so processJoin can consume it even when
        // the player is already online (PlayerJoinEvent fires before ServerSwitchEvent on the
        // proxy, so processJoin may run before perform.login arrives at this backend).
        proxySessionManager.processProxySessionMessage(name, verifiedPremiumUuid);
        Player player = bukkitService.getPlayerExact(name);
        logger.info("performLogin: " + name + " verifiedPremiumUuid=" + verifiedPremiumUuid
            + " playerOnline=" + (player != null && player.isOnline()));
        if (player == null || !player.isOnline()) {
            logger.info(name + " is not yet online; queued for auto-login when they connect.");
            return;
        }
        if (verifiedPremiumUuid == null) {
            completeProxyLogin(player);
            return;
        }
        bukkitService.runTaskAsynchronously(() -> {
            if (!proxyLoginRequestValidator.validate(player, verifiedPremiumUuid)) {
                logger.debug("performLogin: validate() rejected/deferred {0}; removing queued login request", name);
                proxySessionManager.removeLoginRequest(name);
                return;
            }
            logger.info(name + " premium login accepted by validate(); completing proxy login");
            bukkitService.scheduleSyncTaskFromOptionallyAsyncTask(player, () -> {
                if (player.isOnline()) {
                    completeProxyLogin(player);
                }
            });
        });
    }

    private void completeProxyLogin(Player player) {
        // Player is already online: also drive the login directly in case processJoin
        // has already run past the proxy-session check and created a limbo player.
        proxySessionManager.removeLoginRequest(player.getName());
        management.forceLoginFromProxy(player);
        logger.debug("Sending auto-login ACK for {0}", player.getName());
        bungeeSender.sendAuthMeBungeecordMessage(player, MessageType.PERFORM_LOGIN_ACK);
        logger.info(player.getName() + " has been automatically logged in via proxy request.");
    }

    /**
     * Holder for a validated {@code perform.login}: the player name and the proxy-verified premium UUID
     * (or {@code null} if the player is not a verified premium player).
     */
    private static final class VerifiedProxyLogin {

        private final String name;
        private final UUID verifiedPremiumUuid;

        private VerifiedProxyLogin(String name, UUID verifiedPremiumUuid) {
            this.name = name;
            this.verifiedPremiumUuid = verifiedPremiumUuid;
        }
    }

}
