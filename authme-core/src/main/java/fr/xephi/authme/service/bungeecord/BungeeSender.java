package fr.xephi.authme.service.bungeecord;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import fr.xephi.authme.AuthMe;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import fr.xephi.authme.ConsoleLogger;
import fr.xephi.authme.initialization.SettingsDependent;
import fr.xephi.authme.output.ConsoleLoggerFactory;
import fr.xephi.authme.service.BukkitService;
import fr.xephi.authme.settings.Settings;
import fr.xephi.authme.settings.properties.HooksSettings;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.Messenger;

import javax.inject.Inject;

public class BungeeSender implements SettingsDependent {

    private static final String BUNGEE_CHANNEL = "BungeeCord";
    private static final String AUTHME_CHANNEL = MessageType.CHANNEL;

    private final ConsoleLogger logger = ConsoleLoggerFactory.get(BungeeSender.class);
    private final AuthMe plugin;
    private final BukkitService bukkitService;

    private boolean isEnabled;
    private String destinationServerOnLogin;

    /*
     * Constructor.
     */
    @Inject
    BungeeSender(AuthMe plugin, BukkitService bukkitService, Settings settings) {
        this.plugin = plugin;
        this.bukkitService = bukkitService;
        reload(settings);
    }

    @Override
    public void reload(Settings settings) {
        this.isEnabled = settings.getProperty(HooksSettings.BUNGEECORD);
        this.destinationServerOnLogin = settings.getProperty(HooksSettings.BUNGEECORD_SERVER);
        Messenger messenger = plugin.getServer().getMessenger();
        if (messenger == null) {
            return;
        }
        setOutgoingChannel(messenger, BUNGEE_CHANNEL, isEnabled);
        setOutgoingChannel(messenger, AUTHME_CHANNEL, isEnabled);
    }

    /**
     * Registers or unregisters an outgoing channel so that its state matches the given flag.
     *
     * @param messenger the server messenger
     * @param channel   the channel to update
     * @param register  whether the channel should be registered
     */
    private void setOutgoingChannel(Messenger messenger, String channel, boolean register) {
        boolean registered = messenger.isOutgoingChannelRegistered(plugin, channel);
        if (register && !registered) {
            messenger.registerOutgoingPluginChannel(plugin, channel);
        } else if (!register && registered) {
            messenger.unregisterOutgoingPluginChannel(plugin, channel);
        }
    }

    public boolean isEnabled() {
        return isEnabled;
    }

    /**
     * Send a player to a specified server. If no server is configured, this will
     * do nothing.
     *
     * @param player The player to send.
     */
    public void connectPlayerOnLogin(Player player) {
        if (!isEnabled || destinationServerOnLogin.isEmpty()) {
            return;
        }
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Connect");
        out.writeUTF(destinationServerOnLogin);
        byte[] payload = out.toByteArray();
        // Add a small delay, just in case...
        bukkitService.scheduleSyncDelayedTask(player, () ->
            bukkitService.sendBungeeMessage(player, payload), 20L);
    }

    /**
     * Sends a message to the AuthMe plugin messaging channel, if enabled.
     *
     * @param player     The player related to the message
     * @param type       The message type, See {@link MessageType}
     */
    public void sendAuthMeBungeecordMessage(Player player, MessageType type) {
        if (!isEnabled) {
            return;
        }
        if (!plugin.isEnabled()) {
            logger.debug("Tried to send a " + type + " bungeecord message but the plugin was disabled!");
            return;
        }
        bukkitService.sendAuthMePluginMessage(player, buildIdAndNameMessage(type, player.getName()));
    }

    /**
     * Builds the common "message type id + lowercased player name" payload used by the
     * authme:main messages.
     *
     * @param type the message type
     * @param name the player name (lowercased before being written)
     * @return the serialized payload
     */
    private static byte[] buildIdAndNameMessage(MessageType type, String name) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF(type.getId());
        out.writeUTF(name.toLowerCase(Locale.ROOT));
        return out.toByteArray();
    }

    /**
     * Notifies the proxy that the given username has been enrolled as premium.
     * Uses any online player as the message carrier.
     *
     * @param username the player name (will be lowercased)
     */
    public void sendPremiumSet(String username) {
        sendPremiumNotification(MessageType.PREMIUM_SET, username);
    }

    /**
     * Notifies the proxy that a pending premium verification has been started for the given
     * username. The proxy should force Mojang authentication for this player on their next
     * connection but must NOT auto-login them via {@code PERFORM_LOGIN} (that path is reserved
     * for confirmed premium players). Uses any online player as the message carrier.
     *
     * @param username the player name (will be lowercased)
     */
    public void sendPremiumPendingSet(String username) {
        sendPremiumNotification(MessageType.PREMIUM_PENDING_SET, username);
    }

    /**
     * Notifies the proxy that premium mode has been disabled for the given username.
     * Uses any online player as the message carrier.
     *
     * @param username the player name (will be lowercased)
     */
    public void sendPremiumUnset(String username) {
        sendPremiumNotification(MessageType.PREMIUM_UNSET, username);
    }

    /**
     * Sends the full list of premium usernames to the proxy using the given carrier player.
     * The list is split into chunks of at most 1 000 names to stay within the BungeeCord
     * plugin-messaging 32 767-byte limit per message.
     *
     * @param carrier          the player used as message carrier
     * @param premiumUsernames the list of premium usernames (lowercase)
     */
    public void sendPremiumList(Player carrier, List<String> premiumUsernames) {
        if (!isEnabled || !plugin.isEnabled()) {
            return;
        }
        List<String> normalizedUsernames = premiumUsernames.stream()
            .map(name -> name.toLowerCase(Locale.ROOT))
            .collect(Collectors.toList());
        int chunkSize = 1000;
        int total = normalizedUsernames.size();
        if (total == 0) {
            sendPremiumListChunk(carrier, 0, true, "");
            return;
        }
        int numChunks = (total + chunkSize - 1) / chunkSize;
        for (int i = 0; i < numChunks; i++) {
            int fromIndex = i * chunkSize;
            int toIndex = Math.min(fromIndex + chunkSize, total);
            String csv = String.join(",", normalizedUsernames.subList(fromIndex, toIndex));
            sendPremiumListChunk(carrier, i, i == numChunks - 1, csv);
        }
    }

    private void sendPremiumListChunk(Player carrier, int seq, boolean last, String csv) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF(MessageType.PREMIUM_LIST_CHUNK.getId());
        out.writeUTF(seq + ":" + (last ? "1" : "0") + ":" + csv);
        bukkitService.sendAuthMePluginMessage(carrier, out.toByteArray());
    }

    private void sendPremiumNotification(MessageType type, String username) {
        if (!isEnabled || !plugin.isEnabled()) {
            return;
        }
        Optional<? extends Player> carrier = bukkitService.getOnlinePlayers().stream().findFirst();
        if (!carrier.isPresent()) {
            logger.warning("Cannot send premium notification to proxy: no online player available as carrier."
                + " Premium state may be stale on the proxy until the next full resync.");
            return;
        }
        Player p = carrier.get();
        byte[] payload = buildIdAndNameMessage(type, username);
        bukkitService.scheduleSyncTaskFromOptionallyAsyncTask(() ->
            bukkitService.sendAuthMePluginMessage(p, payload));
    }

}
