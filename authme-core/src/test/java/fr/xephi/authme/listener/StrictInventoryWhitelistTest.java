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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.stream.Stream;

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

    @ParameterizedTest(name = "strict={0}, title={1}")
    @MethodSource("allowedTitles")
    void shouldAllowWhitelistedTitle(boolean strict, String title) {
        givenWhitelistWithStrictCheck(strict);
        InventoryOpenEvent event = inventoryOpenEvent(title);

        listener.onPlayerInventoryOpen(event);

        assertThat(event.isCancelled(), equalTo(false));
        verifyNoInteractions(bukkitService);
    }

    private static Stream<Arguments> allowedTitles() {
        return Stream.of(
            Arguments.of(true, "/L"),
            Arguments.of(false, "/lx"));
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
