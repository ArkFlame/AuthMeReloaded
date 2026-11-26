package fr.xephi.authme.listener.packetevents;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowItems;
import fr.xephi.authme.ConsoleLogger;
import fr.xephi.authme.data.auth.PlayerCache;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.output.ConsoleLoggerFactory;
import org.bukkit.entity.Player;

import java.util.Collections;

public class InventoryPacketListener extends PacketListenerAbstract {

    private static final int PLAYER_INVENTORY = 0;
    // Crafting (5) + Armor (4) + Main inventory (27) + Hotbar (9)
    private static final int INVENTORY_SIZE = 45;

    private final ConsoleLogger logger = ConsoleLoggerFactory.get(InventoryPacketListener.class);
    private final PlayerCache playerCache;
    private final DataSource dataSource;

    public InventoryPacketListener(PlayerCache playerCache, DataSource dataSource) {
        this.playerCache = playerCache;
        this.dataSource = dataSource;
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        PacketTypeCommon packetType = event.getPacketType();
        if (packetType != PacketType.Play.Server.WINDOW_ITEMS && packetType != PacketType.Play.Server.SET_SLOT) {
            return;
        }
        int windowId = packetType == PacketType.Play.Server.WINDOW_ITEMS
            ? new WrapperPlayServerWindowItems(event).getWindowId()
            : new WrapperPlayServerSetSlot(event).getWindowId();
        if (windowId == PLAYER_INVENTORY) {
            cancelIfShouldHide(event);
        }
    }

    private void cancelIfShouldHide(PacketSendEvent event) {
        Player player = event.getPlayer();
        if (player != null) {
            String name = player.getName();
            if (!playerCache.isAuthenticated(name) && dataSource.isAuthAvailable(name)) {
                event.setCancelled(true);
            }
        }
    }

    public void sendBlankInventoryPacket(Player player) {
        try {
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, new WrapperPlayServerWindowItems(
                PLAYER_INVENTORY, 0, Collections.nCopies(INVENTORY_SIZE, ItemStack.EMPTY), ItemStack.EMPTY));
        } catch (Exception e) {
            logger.logException("Error during sending blank inventory", e);
        }
    }
}
