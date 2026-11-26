package fr.xephi.authme.listener.packetevents;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import fr.xephi.authme.data.auth.PlayerCache;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.platform.PacketInterceptionAdapter;
import fr.xephi.authme.service.BukkitService;
import fr.xephi.authme.service.PendingPremiumCache;
import fr.xephi.authme.service.PremiumLoginVerifier;
import org.bukkit.entity.Player;

/**
 * PacketEvents-backed implementation of {@link PacketInterceptionAdapter}.
 * Instantiated lazily so PacketEvents classes are only resolved when the dependency is available.
 */
public final class PacketEventsListenerRegistry implements PacketInterceptionAdapter {

    private InventoryPacketListener inventoryPacketListener;
    private TabCompletePacketListener tabCompletePacketListener;
    private PremiumVerificationPacketListener premiumVerificationPacketListener;

    @Override
    public void registerInventoryProtection(PlayerCache playerCache, DataSource dataSource) {
        if (inventoryPacketListener == null) {
            inventoryPacketListener = new InventoryPacketListener(playerCache, dataSource);
        }
        register(inventoryPacketListener);
    }

    @Override
    public void unregisterInventoryProtection() {
        if (inventoryPacketListener != null) {
            unregister(inventoryPacketListener);
            inventoryPacketListener = null;
        }
    }

    @Override
    public void sendBlankInventoryPacket(Player player) {
        if (inventoryPacketListener != null) {
            inventoryPacketListener.sendBlankInventoryPacket(player);
        }
    }

    @Override
    public void registerTabCompleteBlock(PlayerCache playerCache) {
        if (tabCompletePacketListener == null) {
            tabCompletePacketListener = new TabCompletePacketListener(playerCache);
        }
        register(tabCompletePacketListener);
    }

    @Override
    public void unregisterTabCompleteBlock() {
        if (tabCompletePacketListener != null) {
            unregister(tabCompletePacketListener);
            tabCompletePacketListener = null;
        }
    }

    @Override
    public void registerPremiumVerification(DataSource dataSource, PremiumLoginVerifier verifier,
                                            PendingPremiumCache pendingPremiumCache, BukkitService bukkitService) {
        if (premiumVerificationPacketListener == null) {
            premiumVerificationPacketListener =
                new PremiumVerificationPacketListener(dataSource, verifier, pendingPremiumCache, bukkitService);
        }
        register(premiumVerificationPacketListener);
    }

    @Override
    public void unregisterPremiumVerification() {
        if (premiumVerificationPacketListener != null) {
            unregister(premiumVerificationPacketListener);
            premiumVerificationPacketListener = null;
        }
    }

    private static void register(PacketListenerCommon listener) {
        PacketEvents.getAPI().getEventManager().registerListener(listener);
    }

    private static void unregister(PacketListenerCommon listener) {
        PacketEvents.getAPI().getEventManager().unregisterListener(listener);
    }
}
