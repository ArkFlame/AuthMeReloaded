package fr.xephi.authme.listener;

import fr.xephi.authme.data.QuickCommandsProtectionManager;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.data.auth.PlayerCache;
import fr.xephi.authme.data.limbo.EnderPearlRestoreData;
import fr.xephi.authme.data.limbo.LimboService;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.message.Messages;
import fr.xephi.authme.permission.PermissionsManager;
import fr.xephi.authme.permission.PlayerStatePermission;
import fr.xephi.authme.platform.ChatAdapter;
import fr.xephi.authme.platform.TeleportAdapter;
import fr.xephi.authme.process.Management;
import fr.xephi.authme.service.BukkitService;
import fr.xephi.authme.service.JoinMessageService;
import fr.xephi.authme.service.TeleportationService;
import fr.xephi.authme.service.ValidationService;
import fr.xephi.authme.settings.Settings;
import fr.xephi.authme.settings.SpawnLoader;
import fr.xephi.authme.settings.properties.HooksSettings;
import fr.xephi.authme.settings.properties.RegistrationSettings;
import fr.xephi.authme.settings.properties.RestrictionSettings;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.InventoryView;

import javax.inject.Inject;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

import static fr.xephi.authme.settings.properties.RestrictionSettings.ALLOWED_MOVEMENT_RADIUS;
import static fr.xephi.authme.settings.properties.RestrictionSettings.ALLOW_UNAUTHED_MOVEMENT;

/**
 * Listener class for player events.
 */
public class PlayerListener extends AbstractCancelListener implements Listener {

    @Inject
    private Settings settings;
    @Inject
    private Messages messages;
    @Inject
    private DataSource dataSource;
    @Inject
    private Management management;
    @Inject
    private BukkitService bukkitService;
    @Inject
    private SpawnLoader spawnLoader;
    @Inject
    private OnJoinVerifier onJoinVerifier;
    @Inject
    private TeleportationService teleportationService;
    @Inject
    private ValidationService validationService;
    @Inject
    private JoinMessageService joinMessageService;
    @Inject
    private PermissionsManager permissionsManager;
    @Inject
    private QuickCommandsProtectionManager quickCommandsProtectionManager;
    @Inject
    private LimboService limboService;
    @Inject
    private ChatAdapter chatAdapter;
    @Inject
    private TeleportAdapter teleportAdapter;
    @Inject
    private PlayerCache playerCache;

    private static final Set<String> BLOCKED_TELEPORT_CAUSES = new HashSet<>(Arrays.asList(
        "ENDER_PEARL", "CHORUS_FRUIT", "CONSUMABLE_EFFECT", "NETHER_PORTAL",
        "END_PORTAL", "END_GATEWAY", "SPECTATE"));

    // Lowest priority to apply fast protection checks
    @EventHandler(priority = EventPriority.LOWEST)
    public void onAsyncPlayerPreLoginEventLowest(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        final String name = event.getName();

        // NOTE: getAddress() sometimes returning null, we don't want to handle this race condition
        if (event.getAddress() == null) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                messages.retrieveSingle(name, MessageKey.KICK_UNRESOLVED_HOSTNAME));
            return;
        }

        if (validationService.isUnrestricted(name)) {
            return;
        }

        // Non-blocking checks
        try {
            onJoinVerifier.checkIsValidName(name, event.getUniqueId());
        } catch (FailedVerificationException e) {
            denyPreLogin(event, name, e);
        }
    }

    /*
     * Login/join/leave events
     */

    // Note: at this stage (HIGHEST priority) the user's permission data should already have been loaded by
    // the permission handler, we don't need to call permissionsManager.loadUserData()

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAsyncPlayerPreLoginEventHighest(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        final String name = event.getName();

        if (validationService.isUnrestricted(name)) {
            return;
        }

        if (bukkitService.getPlayerExact(name) == null) {
            playerCache.removePlayer(name);
        }

        // Slow, blocking checks
        try {
            final PlayerAuth auth = dataSource.getAuth(name);
            final boolean isAuthAvailable = auth != null;
            // The address is immutable for this event: read it once instead of twice.
            final InetAddress address = event.getAddress();
            onJoinVerifier.checkNameRestrictions(name, address);
            onJoinVerifier.checkKickNonRegistered(isAuthAvailable);
            onJoinVerifier.checkAntibot(name, isAuthAvailable);
            onJoinVerifier.checkNameCasing(name, auth);
            final String ip = address.getHostAddress();
            onJoinVerifier.checkPlayerCountry(name, ip, isAuthAvailable);
            // Preload only once every check passed: a rejected connection never reaches
            // onPlayerJoin, so its entry would stay in the preloaded-status map forever.
            teleportationService.preloadAuthStatus(name, isAuthAvailable);
        } catch (FailedVerificationException e) {
            denyPreLogin(event, name, e);
        }
    }

    /** Shared denial path for both pre-login verification phases. */
    private void denyPreLogin(AsyncPlayerPreLoginEvent event, String name, FailedVerificationException e) {
        event.setKickMessage(messages.retrieveSingle(name, e.getReason(), e.getArgs()));
        event.setLoginResult(AsyncPlayerPreLoginEvent.Result.KICK_OTHER);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoinLowest(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String name = player.getName();
        playerCache.removePlayer(name);
        if (!validationService.isUnrestricted(name)) {
            limboService.revokeOperatorOnJoin(player);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();

        if (!SpawnLocationTracker.isEventCalled()) {
            teleportationService.teleportOnJoin(player);
        }

        teleportationService.clearPreloadedAuthStatus(player.getName());

        quickCommandsProtectionManager.processJoin(player);

        management.performJoin(player);

        teleportationService.teleportNewPlayerToFirstSpawn(player);
    }

    @EventHandler(priority = EventPriority.HIGH) // HIGH as EssentialsX listens at HIGHEST
    public void onJoinMessage(PlayerJoinEvent event) {
        final Player player = event.getPlayer();

        // Note: join message can be null, despite api documentation says not
        if (settings.getProperty(RegistrationSettings.REMOVE_JOIN_MESSAGE)) {
            event.setJoinMessage(null);
            return;
        }

        String customJoinMessage = settings.getProperty(RegistrationSettings.CUSTOM_JOIN_MESSAGE);
        if (!customJoinMessage.isEmpty()) {
            // A player's display name is stable while this event is handled: read it once.
            final String displayName = player.getDisplayName();
            customJoinMessage = ChatColor.translateAlternateColorCodes('&', customJoinMessage);
            event.setJoinMessage(customJoinMessage
                .replace("{PLAYERNAME}", player.getName())
                .replace("{DISPLAYNAME}", displayName)
                .replace("{DISPLAYNAMENOCOLOR}", ChatColor.stripColor(displayName))
            );
        }

        if (!settings.getProperty(RegistrationSettings.DELAY_JOIN_MESSAGE)) {
            return;
        }

        String name = player.getName().toLowerCase(Locale.ROOT);
        String joinMsg = event.getJoinMessage();

        // Remove the join message while the player isn't logging in
        if (joinMsg != null) {
            event.setJoinMessage(null);
            joinMessageService.putMessage(name, joinMsg);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        joinMessageService.removeMessage(player.getName());

        // Note: quit message can be null, despite api documentation says not
        if (settings.getProperty(RegistrationSettings.REMOVE_LEAVE_MESSAGE)
            || settings.getProperty(RegistrationSettings.REMOVE_UNLOGGED_LEAVE_MESSAGE)
                && getListenerService().shouldCancelEvent(event)) {
            event.setQuitMessage(null);
        }

        try {
            // Save in-flight ender pearls and vehicle state to disk so they can be restored on reconnect.
            // Only authenticated players can throw pearls or ride vehicles, so we skip unauthenticated ones.
            if (!getListenerService().shouldCancelEvent(player)) {
                saveStateBeforeQuit(player);
            }

            // Restore speed synchronously before the async quit, so the player's
            // .dat file is saved with the correct speed (not 0.0f).
            if (limboService.hasLimboPlayer(player)) {
                limboService.restoreData(player);
            } else {
                limboService.restorePendingOperator(player);
                limboService.restoreSpeedSync(player);
            }
        } finally {
            management.performQuit(player);
        }
    }

    private void saveStateBeforeQuit(Player player) {
        // Imperative scan: no per-quit stream, capturing filter/map lambdas or collector allocation.
        Set<EnderPearlRestoreData> pearls = new HashSet<>();
        for (World world : player.getServer().getWorlds()) {
            for (EnderPearl pearl : world.getEntitiesByClass(EnderPearl.class)) {
                // Folia: check region ownership before touching the pearl's data.
                if (bukkitService.isOwnedByCurrentThread(pearl) && player.equals(pearl.getShooter())) {
                    pearls.add(new EnderPearlRestoreData(pearl.getUniqueId(), pearl.getLocation(), pearl.getVelocity()));
                }
            }
        }
        if (!pearls.isEmpty()) {
            limboService.saveEnderPearlsForPlayer(player, pearls);
        }

        if (player.isInsideVehicle()) {
            Entity vehicle = player.getVehicle();
            if (vehicle != null) {
                limboService.saveVehicleForPlayer(player, vehicle.getUniqueId(), vehicle.getType());
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onPlayerKick(PlayerKickEvent event) {
        if (!settings.getProperty(RestrictionSettings.FORCE_SINGLE_SESSION)) {
            return;
        }

        // #3114: kick reasons are localized (and differ on Folia), so identify a duplicate login
        // by the second online session; #831: also match the legacy English reason offline.
        if (isDuplicateLoginKick(event) || isLegacyDuplicateKick(chatAdapter.getKickReason(event))) {
            event.setCancelled(true);
        }
    }

    private static boolean isLegacyDuplicateKick(String reason) {
        return reason != null && reason.contains("You logged in from another location");
    }

    private boolean isDuplicateLoginKick(PlayerKickEvent event) {
        Collection<Player> onlinePlayers = bukkitService.getOnlinePlayers();
        if (onlinePlayers == null || onlinePlayers.isEmpty()) {
            return false;
        }
        Player kicked = event.getPlayer();
        String name = kicked == null ? null : kicked.getName();
        if (name == null) {
            return false;
        }
        boolean kickedIsOnline = false;
        boolean duplicateSession = false;
        for (Player online : onlinePlayers) {
            if (online == kicked) {
                kickedIsOnline = true;
            } else if (online != null && name.equalsIgnoreCase(online.getName())) {
                duplicateSession = true;
            }
        }
        // Defer the cache/limbo lookups until a second session is confirmed: most kicks have none.
        return duplicateSession
            && (playerCache.isAuthenticated(name) || limboService.hasLimboPlayer(kicked) || kickedIsOnline);
    }

    /*
     * Chat/command events
     */

    private void removeUnauthorizedRecipients(AsyncPlayerChatEvent event) {
        // Iterate directly instead of removeIf(listenerService::shouldCancelEvent), which would
        // allocate a new capturing predicate for every chat event.
        Set<Player> recipients = event.getRecipients();
        ListenerService listenerService = getListenerService();
        for (Iterator<Player> it = recipients.iterator(); it.hasNext(); ) {
            if (listenerService.shouldCancelEvent(it.next())) {
                it.remove();
            }
        }
        if (recipients.isEmpty()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (settings.getProperty(RestrictionSettings.ALLOW_CHAT)) {
            return;
        }

        final Player player = event.getPlayer();
        if (!getListenerService().shouldCancelEvent(player)
            || permissionsManager.hasPermission(player, PlayerStatePermission.ALLOW_CHAT_BEFORE_LOGIN)) {
            // Read HIDE_CHAT only on the path that actually needs it.
            if (settings.getProperty(RestrictionSettings.HIDE_CHAT)) {
                removeUnauthorizedRecipients(event);
            }
            return;
        }
        event.setCancelled(true);
        messages.send(player, MessageKey.DENIED_CHAT);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        // Avoid String#split's array allocation on this hot event: only the first token is needed.
        String message = event.getMessage();
        int spaceIndex = message.indexOf(' ');
        String cmd = (spaceIndex < 0 ? message : message.substring(0, spaceIndex)).toLowerCase(Locale.ROOT);
        if (settings.getProperty(HooksSettings.USE_ESSENTIALS_MOTD) && "/motd".equals(cmd)) {
            return;
        }
        if (settings.getProperty(RestrictionSettings.ALLOW_COMMANDS).contains(cmd)) {
            return;
        }
        final Player player = event.getPlayer();
        if (!quickCommandsProtectionManager.isAllowed(player.getName())) {
            event.setCancelled(true);
            player.kickPlayer(messages.retrieveSingle(player, MessageKey.QUICK_COMMAND_PROTECTION_KICK));
            return;
        }
        if (getListenerService().shouldCancelEvent(player)) {
            event.setCancelled(true);
            messages.send(player, MessageKey.DENIED_COMMAND);
        }
    }

    /*
     * Movement events
     */

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onPlayerMove(PlayerMoveEvent event) {
        // PlayerMoveEvent is one of the hottest events: read each setting once instead of twice.
        boolean allowUnauthedMovement = settings.getProperty(ALLOW_UNAUTHED_MOVEMENT);
        int allowedRadius = allowUnauthedMovement ? settings.getProperty(ALLOWED_MOVEMENT_RADIUS) : 0;
        if (allowUnauthedMovement && allowedRadius <= 0) {
            return;
        }

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return;
        }

        /*
         * Limit player X and Z movements to 1 block
         * Deny player Y+ movements (allows falling)
         */

        if (from.getBlockX() == to.getBlockX()
            && from.getBlockZ() == to.getBlockZ()
            && from.getY() - to.getY() >= 0) {
            return;
        }

        Player player = event.getPlayer();
        if (!getListenerService().shouldCancelEvent(player)) {
            return;
        }

        if (!allowUnauthedMovement) {
            // "cancel" the event
            event.setTo(from);
            return;
        }

        if (settings.getProperty(RestrictionSettings.NO_TELEPORT)) {
            return;
        }

        Location spawn = spawnLoader.getSpawnLocation(player);
        if (spawn != null && spawn.getWorld() != null) {
            if (!player.getWorld().equals(spawn.getWorld())) {
                teleportAdapter.teleportPlayer(player, spawn);
                return;
            }
            Location current = player.getLocation();
            if (current == null) {
                current = from;
            }
            // The radius is positive here, so comparing squared distances is equivalent to
            // distance(...) > allowedRadius but avoids two square roots on this hot path.
            double radiusSquared = (double) allowedRadius * allowedRadius;
            if (spawn.distanceSquared(current) > radiusSquared) {
                // Already outside the radius: there is no in-radius location to fall back to.
                teleportAdapter.teleportPlayer(player, spawn);
            } else if (spawn.distanceSquared(to) > radiusSquared) {
                // #3081: prevent crossing the boundary without teleporting the player to spawn.
                event.setTo(from);
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (BLOCKED_TELEPORT_CAUSES.contains(event.getCause().name())) {
            cancelIfUnauthorized(event);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onPlayerPortal(PlayerPortalEvent event) { cancelIfUnauthorized(event); }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (!getListenerService().shouldCancelEvent(event)) {
            return;
        }
        if (!settings.getProperty(RestrictionSettings.NO_TELEPORT)) {
            Location spawn = spawnLoader.getPlayerRespawnLocationOrSpawn(player);
            if (spawn != null && spawn.getWorld() != null) {
                event.setRespawnLocation(spawn);
            }
        }
        // Bukkit sends a fresh PlayerAbilities packet on respawn that resets walk/fly speed,
        // overriding the restriction set during join. Re-apply one tick later.
        bukkitService.runTaskLater(player, () -> limboService.reapplyLimboRestrictions(player), 1L);
    }

    /*
     * Entity/block interaction events
     */

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteract(PlayerInteractEvent event) { cancelIfUnauthorized(event); }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) { cancelIfUnauthorized(event); }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) { cancelIfUnauthorized(event); }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onPlayerHitPlayerEvent(EntityDamageByEntityEvent event) { cancelIfUnauthorized(event); }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onPlayerShear(PlayerShearEntityEvent event) { cancelIfUnauthorized(event); }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onPlayerFish(PlayerFishEvent event) { cancelIfUnauthorized(event); }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onPlayerBedEnter(PlayerBedEnterEvent event) { cancelIfUnauthorized(event); }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onPlayerEditBook(PlayerEditBookEvent event) { cancelIfUnauthorized(event); }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onSignChange(SignChangeEvent event) { cancelIfUnauthorized(event.getPlayer(), event); }

    /*
     * Inventory interactions
     */

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onPlayerDropItem(PlayerDropItemEvent event) { cancelIfUnauthorized(event); }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onPlayerHeldItem(PlayerItemHeldEvent event) { cancelIfUnauthorized(event); }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onPlayerConsumeItem(PlayerItemConsumeEvent event) { cancelIfUnauthorized(event); }

    // Port of HaHaWTH/AuthMeReReloaded @ 06f6035b0: exact or substring inventory title matching
    private boolean isInventoryWhitelisted(InventoryView inventory) {
        if (inventory == null) {
            return false;
        }
        Set<String> whitelist = settings.getProperty(RestrictionSettings.UNRESTRICTED_INVENTORIES);
        if (whitelist.isEmpty()) {
            return false;
        }
        String invName = ChatColor.stripColor(inventory.getTitle()).toLowerCase(Locale.ROOT);
        if (settings.getProperty(RestrictionSettings.STRICT_UNRESTRICTED_INVENTORIES_CHECK)) {
            return whitelist.contains(invName);
        }
        for (String entry : whitelist) {
            if (invName.contains(entry)) {
                return true;
            }
        }
        return false;
    }

    // Shared by open/click/drag: the player is passed in so each event keeps using its own getter.
    private boolean isUnauthorizedInventoryAccess(InventoryEvent event, HumanEntity player) {
        return getListenerService().shouldCancelEvent(player)
            && !isInventoryWhitelisted(event.getView());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onPlayerInventoryOpen(InventoryOpenEvent event) {
        final HumanEntity player = event.getPlayer();

        if (isUnauthorizedInventoryAccess(event, player)) {
            event.setCancelled(true);

            /*
             * @note little hack cause InventoryOpenEvent cannot be cancelled for
             * real, cause no packet is sent to server by client for the main inv
             */
            bukkitService.scheduleSyncDelayedTask((Player) player, player::closeInventory, 1);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onPlayerInventoryClick(InventoryClickEvent event) {
        cancelEvent(isUnauthorizedInventoryAccess(event, event.getWhoClicked()), event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onPlayerInventoryDrag(InventoryDragEvent event) {
        cancelEvent(isUnauthorizedInventoryAccess(event, event.getWhoClicked()), event);
    }

}
