package fr.xephi.authme.service;

import fr.xephi.authme.ConsoleLogger;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.data.auth.PlayerCache;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.initialization.HasCleanup;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.message.Messages;
import fr.xephi.authme.output.ConsoleLoggerFactory;
import fr.xephi.authme.service.bungeecord.BungeeSender;
import fr.xephi.authme.settings.Settings;
import fr.xephi.authme.settings.properties.PremiumSettings;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import javax.inject.Inject;
import java.util.Optional;
import java.util.UUID;

/**
 * Handles premium mode — allowing players with an official Minecraft account to skip authentication.
 */
public class PremiumService implements HasCleanup {

    private final ConsoleLogger logger = ConsoleLoggerFactory.get(PremiumService.class);

    @Inject
    private Settings settings;

    @Inject
    private DataSource dataSource;

    @Inject
    private PlayerCache playerCache;

    @Inject
    private Messages messages;

    @Inject
    private BukkitService bukkitService;

    @Inject
    private BungeeSender bungeeSender;

    @Inject
    private MojangApiService mojangApiService;

    @Inject
    private PendingPremiumCache pendingPremiumCache;

    @Inject
    private PremiumAutoRegisterService premiumAutoRegisterService;

    PremiumService() {
    }

    /**
     * Delegates the optional auto-registration of a verified premium player who has no account.
     *
     * @param player the joining player
     * @return true if auto-registration was scheduled and the join flow should stop
     */
    public boolean tryAutoRegister(Player player) {
        return premiumAutoRegisterService.tryAutoRegister(player);
    }

    /**
     * Enables premium mode for the given player. Verifies the player is logged in, that the feature
     * is enabled, and that the player has an official Minecraft account via the Mojang API.
     * On success, stores the Mojang UUID as {@code premium_uuid} in the database.
     *
     * @param player the player requesting premium mode
     */
    public void enablePremium(Player player) {
        PlayerAuth auth = getAuthForPlayerCommand(player);
        if (auth == null) {
            return;
        }

        if (auth.isPremium()) {
            messages.send(player, MessageKey.PREMIUM_ALREADY_ENABLED);
            return;
        }

        UUID playerUuid = player.getUniqueId();
        if (playerUuid.version() == 4) {
            // UUID v4 = Mojang-issued UUID: either the server is in online mode (Mojang already
            // verified the session at the connection level) or the proxy forwarded a real Mojang
            // UUID. No extra API call needed — the server's own auth is the source of truth.
            storePremiumUuid(auth, playerUuid, player, player.getName(), true,
                MessageKey.PREMIUM_ENABLE_SUCCESS);
        } else {
            // UUID v3 = Bukkit offline UUID: fetch the Mojang UUID and hold it as pending.
            // The player must reconnect; a cryptographic Mojang session check on reconnect is
            // what actually confirms ownership before we write anything to the database.
            String playerName = player.getName();
            bukkitService.runTaskOptionallyAsync(() -> {
                Optional<UUID> mojangUuid = mojangApiService.fetchUuidByName(playerName);
                if (!mojangUuid.isPresent()) {
                    messages.send(player, MessageKey.PREMIUM_ACCOUNT_NOT_FOUND);
                    return;
                }
                setPendingPremium(playerName, mojangUuid.get());
                kickPlayerWithMessage(player, MessageKey.PREMIUM_PENDING_KICK);
            });
        }
    }

    /**
     * Disables premium mode for the given player.
     *
     * @param player the player requesting to disable premium mode
     */
    public void disablePremium(Player player) {
        PlayerAuth auth = getAuthForPlayerCommand(player);
        if (auth == null) {
            return;
        }

        if (!auth.isPremium()) {
            messages.send(player, MessageKey.PREMIUM_NOT_ENABLED);
            return;
        }

        auth.setPremiumUuid(null);
        String playerName = player.getName();
        bukkitService.runTaskOptionallyAsync(() -> disablePremiumAsync(auth, player, playerName));
    }

    private void disablePremiumAsync(PlayerAuth auth, Player player, String playerName) {
        if (dataSource.updatePremiumUuid(auth)) {
            playerCache.updatePlayerIfPresent(auth);
            bungeeSender.sendPremiumUnset(playerName);
            messages.send(player, MessageKey.PREMIUM_DISABLE_SUCCESS);
        } else {
            logger.warning("Failed to clear premium UUID for player " + playerName);
            messages.send(player, MessageKey.PREMIUM_ERROR);
        }
    }

    /**
     * Enables premium mode for the given player name, callable by an admin from the console or in-game.
     * Verifies that the feature is enabled, that the player is registered, that they don't already have premium,
     * and that a Mojang account exists for the name. If an impostor (a player with the same name but a different
     * UUID) is currently online, they are kicked before the premium UUID is saved.
     *
     * @param sender     the command sender (admin or console)
     * @param playerName the name of the player to enable premium for
     */
    public void enablePremiumAdmin(CommandSender sender, String playerName) {
        if (!isPremiumFeatureEnabled(sender)) {
            return;
        }
        bukkitService.runTaskOptionallyAsync(() -> enablePremiumAdminAsync(sender, playerName));
    }

    private void enablePremiumAdminAsync(CommandSender sender, String playerName) {
        PlayerAuth auth = dataSource.getAuth(playerName);
        if (auth == null) {
            messages.send(sender, MessageKey.PREMIUM_ADMIN_NOT_REGISTERED, playerName);
            return;
        }

        if (auth.isPremium()) {
            messages.send(sender, MessageKey.PREMIUM_ADMIN_ALREADY_ENABLED, playerName);
            return;
        }

        Optional<UUID> mojangUuid = mojangApiService.fetchUuidByName(playerName);
        if (!mojangUuid.isPresent()) {
            messages.send(sender, MessageKey.PREMIUM_ADMIN_ACCOUNT_NOT_FOUND, playerName);
            return;
        }
        finishEnablePremiumAdmin(sender, playerName, auth, mojangUuid.get());
    }

    private void finishEnablePremiumAdmin(CommandSender sender, String playerName, PlayerAuth auth, UUID mojangUuid) {
        Player onlinePlayer = bukkitService.getPlayerExact(playerName);
        UUID onlineUuid = onlinePlayer == null ? null : onlinePlayer.getUniqueId();
        // If someone is online with that name but a different UUID, they are an impostor — kick them
        if (onlineUuid != null && !mojangUuid.equals(onlineUuid)) {
            kickPlayerWithMessage(onlinePlayer, MessageKey.PREMIUM_ADMIN_KICK_REASON);
            messages.send(sender, MessageKey.PREMIUM_ADMIN_IMPOSTOR_KICKED, playerName);
        }

        // If the online player has a UUID v4 equal to the Mojang UUID the server/proxy already
        // verified their identity; save directly. Otherwise hold as pending and require the
        // player to reconnect so the crypto Mojang session check can confirm ownership.
        if (onlineUuid != null && onlineUuid.version() == 4 && mojangUuid.equals(onlineUuid)) {
            storePremiumUuid(auth, mojangUuid, sender, playerName, true,
                MessageKey.PREMIUM_ADMIN_ENABLE_SUCCESS, playerName);
        } else {
            setPendingPremium(playerName, mojangUuid);
            messages.send(sender, MessageKey.PREMIUM_ADMIN_PENDING, playerName);
            if (onlinePlayer != null) {
                kickPlayerWithMessage(onlinePlayer, MessageKey.PREMIUM_PENDING_KICK);
            }
        }
    }

    /**
     * Disables premium mode for the given player name, callable by an admin from the console or in-game.
     * If the player is currently online (regardless of whether their UUID matches the stored premium UUID),
     * they are kicked so they must reauthenticate with a password on their next connection.
     *
     * @param sender     the command sender (admin or console)
     * @param playerName the name of the player to disable premium for
     */
    public void disablePremiumAdmin(CommandSender sender, String playerName) {
        if (!isPremiumFeatureEnabled(sender)) {
            return;
        }
        bukkitService.runTaskOptionallyAsync(() -> disablePremiumAdminAsync(sender, playerName));
    }

    private void disablePremiumAdminAsync(CommandSender sender, String playerName) {
        PlayerAuth auth = dataSource.getAuth(playerName);
        if (auth == null) {
            messages.send(sender, MessageKey.PREMIUM_ADMIN_NOT_REGISTERED, playerName);
            return;
        }

        if (!auth.isPremium()) {
            messages.send(sender, MessageKey.PREMIUM_ADMIN_NOT_ENABLED, playerName);
            return;
        }

        auth.setPremiumUuid(null);
        if (!dataSource.updatePremiumUuid(auth)) {
            logger.warning("Failed to clear premium UUID for player " + playerName);
            messages.send(sender, MessageKey.PREMIUM_ERROR);
            return;
        }
        bungeeSender.sendPremiumUnset(playerName);
        playerCache.updatePlayerIfPresent(auth);

        Player onlinePlayer = bukkitService.getPlayerExact(playerName);
        if (onlinePlayer != null) {
            kickPlayerWithMessage(onlinePlayer, MessageKey.PREMIUM_ADMIN_KICK_REASON);
        }
        messages.send(sender, MessageKey.PREMIUM_ADMIN_DISABLE_SUCCESS, playerName);
    }

    @Override
    public void performCleanup() {
        pendingPremiumCache.evictExpired().forEach(bungeeSender::sendPremiumUnset);
    }

    /**
     * Finalizes a pending premium verification: saves the confirmed Mojang UUID to the database,
     * notifies the proxy, and sends a success message to the player.
     * Does NOT update the player cache — the player is not yet logged in at this point;
     * the subsequent forceLogin() call will add them to the cache once authentication completes.
     * Must be called from an async context.
     *
     * @param player        the player whose premium status is being confirmed
     * @param confirmedUuid the Mojang UUID that was cryptographically verified
     */
    public void finalizePendingPremium(Player player, UUID confirmedUuid) {
        String playerName = player.getName();
        PlayerAuth auth = Optional.ofNullable(playerCache.getAuth(playerName))
                .orElseGet(() -> dataSource.getAuth(playerName.toLowerCase(java.util.Locale.ROOT)));
        if (auth == null) {
            logger.warning("Could not finalize pending premium for " + playerName + ": no auth record found");
            return;
        }
        storePremiumUuid(auth, confirmedUuid, player, playerName, false, MessageKey.PREMIUM_ENABLE_SUCCESS);
    }

    /** Sends the shared error messages and returns the player's auth, or null if the command cannot proceed. */
    private PlayerAuth getAuthForPlayerCommand(Player player) {
        if (!isPremiumFeatureEnabled(player)) {
            return null;
        }
        PlayerAuth auth = playerCache.getAuth(player.getName());
        if (auth == null) messages.send(player, MessageKey.NOT_LOGGED_IN);
        return auth;
    }

    private boolean isPremiumFeatureEnabled(CommandSender sender) {
        boolean enabled = settings.getProperty(PremiumSettings.ENABLE_PREMIUM);
        if (!enabled) {
            messages.send(sender, MessageKey.PREMIUM_FEATURE_DISABLED);
        }
        return enabled;
    }

    private void storePremiumUuid(PlayerAuth auth, UUID uuid, CommandSender feedbackTarget, String playerName,
                                    boolean updateCache, MessageKey successKey, String... successArgs) {
        auth.setPremiumUuid(uuid);
        if (!dataSource.updatePremiumUuid(auth)) {
            logger.warning("Failed to save premium UUID for player " + playerName);
            messages.send(feedbackTarget, MessageKey.PREMIUM_ERROR);
            return;
        }
        if (updateCache) {
            playerCache.updatePlayerIfPresent(auth);
        }
        bungeeSender.sendPremiumSet(playerName);
        messages.send(feedbackTarget, successKey, successArgs);
    }

    private void setPendingPremium(String playerName, UUID mojangUuid) {
        pendingPremiumCache.addPending(playerName, mojangUuid).forEach(bungeeSender::sendPremiumUnset);
        bungeeSender.sendPremiumPendingSet(playerName);
    }

    private void kickPlayerWithMessage(Player player, MessageKey key) {
        String kickMsg = messages.retrieveSingle(player, key);
        bukkitService.scheduleSyncTaskFromOptionallyAsyncTask(player, () -> player.kickPlayer(kickMsg));
    }

}
