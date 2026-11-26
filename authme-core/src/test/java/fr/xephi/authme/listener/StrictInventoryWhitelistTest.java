package fr.xephi.authme.listener;

import fr.xephi.authme.service.BukkitService;
import fr.xephi.authme.settings.Settings;
import fr.xephi.authme.settings.properties.RestrictionSettings;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.InventoryView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Tests for the strict unrestricted-inventory matching toggle.
 */
@ExtendWith(MockitoExtension.class)
class StrictInventoryWhitelistTest {

    @InjectMocks
    private PlayerListener listener;

    @Mock
    private Settings settings;
    @Mock
    private ListenerService listenerService;
    @Mock
    private BukkitService bukkitService;

    @Test
    void shouldBlockSimilarTitleWhenStrictCheckIsEnabled() {
        givenWhitelistWithStrictCheck(true);
        InventoryOpenEvent event = inventoryOpenEvent("/lx");

        listener.onPlayerInventoryOpen(event);

        assertThat(event.isCancelled(), equalTo(true));
    }

    @Test
    void shouldAllowExactTitleWhenStrictCheckIsEnabled() {
        givenWhitelistWithStrictCheck(true);
        InventoryOpenEvent event = inventoryOpenEvent("/L");

        listener.onPlayerInventoryOpen(event);

        assertThat(event.isCancelled(), equalTo(false));
        verifyNoInteractions(bukkitService);
    }

    @Test
    void shouldAllowSimilarTitleWhenStrictCheckIsDisabled() {
        givenWhitelistWithStrictCheck(false);
        InventoryOpenEvent event = inventoryOpenEvent("/lx");

        listener.onPlayerInventoryOpen(event);

        assertThat(event.isCancelled(), equalTo(false));
        verifyNoInteractions(bukkitService);
    }

    private void givenWhitelistWithStrictCheck(boolean strict) {
        given(settings.getProperty(RestrictionSettings.UNRESTRICTED_INVENTORIES))
            .willReturn(Collections.singleton("/l"));
        given(settings.getProperty(RestrictionSettings.STRICT_UNRESTRICTED_INVENTORIES_CHECK)).willReturn(strict);
        given(listenerService.shouldCancelEvent(any(HumanEntity.class))).willReturn(true);
    }

    private static InventoryOpenEvent inventoryOpenEvent(String title) {
        HumanEntity player = mock(Player.class);
        InventoryView view = mock(InventoryView.class);
        given(view.getTitle()).willReturn(title);
        given(view.getPlayer()).willReturn(player);
        return new InventoryOpenEvent(view);
    }
}
