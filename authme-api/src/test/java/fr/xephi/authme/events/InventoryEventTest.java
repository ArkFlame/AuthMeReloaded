package fr.xephi.authme.events;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/** Focused checks for inventory protection and restoration events. */
class InventoryEventTest {

    private final Player player = mock(Player.class);
    private final PlayerInventory inventory = mock(PlayerInventory.class);
    private final ItemStack[] contents = { mock(ItemStack.class) };
    private final ItemStack[] armor = { mock(ItemStack.class) };

    @Test
    void protectEventSnapshotsInventoryAndArmor() {
        given(player.getInventory()).willReturn(inventory);
        given(inventory.getContents()).willReturn(contents);
        given(inventory.getArmorContents()).willReturn(armor);

        ProtectInventoryEvent event = new ProtectInventoryEvent(player, true);

        assertThat(event.getPlayer(), sameInstance(player));
        assertThat(event.getStoredInventory(), sameInstance(contents));
        assertThat(event.getStoredArmor(), sameInstance(armor));
        assertThat(event.isAsynchronous(), equalTo(true));
        assertThat(event.isCancelled(), equalTo(false));
    }

    @Test
    void restoreEventIsSynchronousAndCancellable() {
        RestoreInventoryEvent event = new RestoreInventoryEvent(player);

        assertThat(event.getPlayer(), sameInstance(player));
        assertThat(event.isAsynchronous(), equalTo(false));

        event.setCancelled(true);
        assertThat(event.isCancelled(), equalTo(true));
    }
}
