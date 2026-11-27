package fr.xephi.authme.listener.packetevents;

import ch.jalu.injector.annotations.NoFieldScan;
import fr.xephi.authme.ConsoleLogger;
import fr.xephi.authme.data.auth.PlayerCache;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.initialization.SettingsDependent;
import fr.xephi.authme.output.ConsoleLoggerFactory;
import fr.xephi.authme.platform.PacketInterceptionAdapter;
import fr.xephi.authme.service.BukkitService;
import fr.xephi.authme.service.PendingPremiumCache;
import fr.xephi.authme.service.PremiumLoginVerifier;
import fr.xephi.authme.settings.Settings;
import fr.xephi.authme.settings.properties.HooksSettings;
import fr.xephi.authme.settings.properties.PremiumSettings;
import fr.xephi.authme.settings.properties.RestrictionSettings;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import javax.inject.Inject;
import java.util.concurrent.atomic.AtomicBoolean;

@NoFieldScan
public class PacketEventsService implements SettingsDependent {

    private final ConsoleLogger logger = ConsoleLoggerFactory.get(PacketEventsService.class);

    private boolean protectInvBeforeLogin;
    private boolean denyTabCompleteBeforeLogin;
    private boolean enablePremium;
    private boolean bungeecordEnabled;
    private final AtomicBoolean inventoryProtectionRegistered = new AtomicBoolean();
    private final AtomicBoolean tabCompleteBlockRegistered = new AtomicBoolean();
    private final AtomicBoolean premiumVerificationRegistered = new AtomicBoolean();

    private final BukkitService bukkitService;
    private final PlayerCache playerCache;
    private final DataSource dataSource;
    private final PacketInterceptionAdapter packetInterceptionAdapter;
    private final PremiumLoginVerifier premiumLoginVerifier;
    private final PendingPremiumCache pendingPremiumCache;

    @Inject
    PacketEventsService(Settings settings, BukkitService bukkitService, PlayerCache playerCache,
                        DataSource dataSource, PacketInterceptionAdapter packetInterceptionAdapter,
                        PremiumLoginVerifier premiumLoginVerifier, PendingPremiumCache pendingPremiumCache) {
        this.bukkitService = bukkitService;
        this.playerCache = playerCache;
        this.dataSource = dataSource;
        this.packetInterceptionAdapter = packetInterceptionAdapter;
        this.premiumLoginVerifier = premiumLoginVerifier;
        this.pendingPremiumCache = pendingPremiumCache;
        reload(settings);
    }

    /**
     * Sets up the PacketEvents packet listeners.
     */
    public void setup() {
        boolean isProxyMode = bungeecordEnabled || packetInterceptionAdapter.isProxyForwardingEnabled();
        boolean needsPremiumPacketVerification = enablePremium
            && !Bukkit.getServer().getOnlineMode()
            && !isProxyMode;

        boolean packetEventsAvailable = Bukkit.getPluginManager().isPluginEnabled("packetevents");
        if (!packetEventsAvailable) {
            if (protectInvBeforeLogin) {
                logger.warning("WARNING! The protectInventory feature requires PacketEvents! Disabling it...");
            }
            if (denyTabCompleteBeforeLogin) {
                logger.warning("WARNING! The denyTabComplete feature requires PacketEvents! Disabling it...");
            }
            if (needsPremiumPacketVerification) {
                logger.warning("WARNING! Premium bypass requires the PacketEvents plugin for session "
                    + "verification! Premium auto-login is disabled until PacketEvents is installed.");
            }
            return;
        }

        if (protectInvBeforeLogin) {
            if (!inventoryProtectionRegistered.get()) {
                packetInterceptionAdapter.registerInventoryProtection(playerCache, dataSource);
                inventoryProtectionRegistered.set(true);
                // Send blank packets to online unauthenticated players (reload scenario)
                for (Player player : bukkitService.getOnlinePlayers()) {
                    if (!playerCache.isAuthenticated(player.getName())
                            && dataSource.isAuthAvailable(player.getName())) {
                        packetInterceptionAdapter.sendBlankInventoryPacket(player);
                    }
                }
            }
        } else {
            unregister(inventoryProtectionRegistered, packetInterceptionAdapter::unregisterInventoryProtection);
        }

        if (denyTabCompleteBeforeLogin) {
            if (!tabCompleteBlockRegistered.get()) {
                packetInterceptionAdapter.registerTabCompleteBlock(playerCache);
                tabCompleteBlockRegistered.set(true);
            }
        } else {
            unregister(tabCompleteBlockRegistered, packetInterceptionAdapter::unregisterTabCompleteBlock);
        }

        if (needsPremiumPacketVerification) {
            if (!premiumVerificationRegistered.get()) {
                packetInterceptionAdapter.registerPremiumVerification(dataSource, premiumLoginVerifier,
                    pendingPremiumCache, bukkitService);
                premiumVerificationRegistered.set(true);
            }
        } else {
            unregister(premiumVerificationRegistered, packetInterceptionAdapter::unregisterPremiumVerification);
        }
    }

    /**
     * Stops all PacketEvents-based features.
     */
    public void disable() {
        unregister(inventoryProtectionRegistered, packetInterceptionAdapter::unregisterInventoryProtection);
        unregister(tabCompleteBlockRegistered, packetInterceptionAdapter::unregisterTabCompleteBlock);
        unregister(premiumVerificationRegistered, packetInterceptionAdapter::unregisterPremiumVerification);
    }

    /** Unregisters a feature only if it is currently registered, preserving the previous flag state on failure. */
    private static void unregister(AtomicBoolean registered, Runnable action) {
        if (registered.get()) {
            action.run();
            registered.set(false);
        }
    }

    /**
     * Sends a blank inventory packet to the given player.
     *
     * @param player the player to send the blank inventory to
     */
    public void sendBlankInventoryPacket(Player player) {
        if (inventoryProtectionRegistered.get()) {
            packetInterceptionAdapter.sendBlankInventoryPacket(player);
        }
    }

    @Override
    public void reload(Settings settings) {
        boolean oldProtectInventory = this.protectInvBeforeLogin;

        this.protectInvBeforeLogin = settings.getProperty(RestrictionSettings.PROTECT_INVENTORY_BEFORE_LOGIN);
        this.denyTabCompleteBeforeLogin = settings.getProperty(RestrictionSettings.DENY_TABCOMPLETE_BEFORE_LOGIN);
        this.enablePremium = settings.getProperty(PremiumSettings.ENABLE_PREMIUM);
        this.bungeecordEnabled = settings.getProperty(HooksSettings.BUNGEECORD);

        // If inventory protection was on and is now disabled, restore inventories for online players
        if (oldProtectInventory && !protectInvBeforeLogin && inventoryProtectionRegistered.get()) {
            unregister(inventoryProtectionRegistered, packetInterceptionAdapter::unregisterInventoryProtection);
            for (Player onlinePlayer : bukkitService.getOnlinePlayers()) {
                if (!playerCache.isAuthenticated(onlinePlayer.getName())) {
                    onlinePlayer.updateInventory();
                }
            }
        }
        setup();
    }
}
