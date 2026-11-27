package fr.xephi.authme.platform;

import fr.xephi.authme.listener.BlockListener;
import org.mockito.MockMakers;
import fr.xephi.authme.listener.DoubleLoginFixListener;
import fr.xephi.authme.listener.LoginLocationFixListener;
import fr.xephi.authme.listener.EntityAirChangeListener;
import fr.xephi.authme.listener.EntityListener;
import fr.xephi.authme.listener.EntityPickupItemListener;
import fr.xephi.authme.listener.PaperChatListener;
import fr.xephi.authme.listener.PaperDialogFlowListener;
import fr.xephi.authme.listener.PaperProxyAutoLoginListener;
import fr.xephi.authme.listener.PaperLoginValidationListener;
import fr.xephi.authme.listener.PaperPlayerSpawnLocationListener;
import fr.xephi.authme.listener.PlayerListener;
import fr.xephi.authme.listener.PlayerOpenSignListener;
import fr.xephi.authme.listener.PlayerSwapHandItemsListener;
import fr.xephi.authme.listener.ServerListener;
import fr.xephi.authme.process.register.RegisterSecondaryArgument;
import fr.xephi.authme.process.register.RegistrationType;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerKickEvent;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

public class PaperPlatformAdapterTest {

    private final PaperPlatformAdapter adapter = new PaperPlatformAdapter();
    private final DialogWindowSpec dialog = new DialogWindowSpec("Title",
        List.of(new DialogInputSpec("password", "Password", 100)),
        "Submit",
        "Cancel",
        false,
        false,
        null);

    @Test
    public void getPlatformNameReturnsExpectedValue() {
        assertThat(adapter.getPlatformName(), is("paper-1.21"));
    }

    @Test
    public void getCompatibilityErrorReturnsNullForCompatibleClasspath() {
        assertThat(adapter.getCompatibilityError(), is((String) null));
    }

    @Test
    public void isDialogSupportedReturnsTrueWhenApiIsOnClasspath() {
        // The Paper dialog API is a test dependency, so DIALOG_AVAILABLE is true
        assertThat(adapter.isDialogSupported(), is(true));
    }

    @Test
    public void getListenersContainsCoreAndPaperListeners() {
        List<Class<? extends Listener>> listeners = adapter.getListeners();

        assertThat(listeners, containsInAnyOrder(
            PlayerListener.class,
            BlockListener.class,
            EntityListener.class,
            ServerListener.class,
            DoubleLoginFixListener.class,
            LoginLocationFixListener.class,
            PlayerSwapHandItemsListener.class,
            EntityAirChangeListener.class,
            EntityPickupItemListener.class,
            PaperChatListener.class,
            PaperDialogFlowListener.class,
            PaperProxyAutoLoginListener.class,
            PaperPlayerSpawnLocationListener.class,
            PaperLoginValidationListener.class,
            PlayerOpenSignListener.class));
    }

    @Test
    public void getListenersRetainsAllModernEventProtections() {
        List<Class<? extends Listener>> listeners = adapter.getListeners();
        assertThat(listeners, hasItems(
            PlayerSwapHandItemsListener.class,
            EntityAirChangeListener.class,
            EntityPickupItemListener.class));
    }

    @Test
    public void dialogMethodsDelegateToPaperDialogHelper() {
        // given - PaperDialogHelper uses Paper API calls that require a running server. Use mockStatic
        // to verify delegation without invoking the full Paper server stack.
        Player player = mock(Player.class, withSettings().mockMaker(MockMakers.INLINE));
        try (MockedStatic<PaperDialogHelper> helperMock = mockStatic(PaperDialogHelper.class, withSettings().mockMaker(MockMakers.INLINE))) {
            // when
            adapter.showLoginDialog(player, dialog);
            adapter.showTotpDialog(player, dialog);
            adapter.showRegisterDialog(player, RegistrationType.EMAIL, RegisterSecondaryArgument.CONFIRMATION, dialog);

            // then
            helperMock.verify(() -> PaperDialogHelper.showLoginDialog(player, dialog));
            helperMock.verify(() -> PaperDialogHelper.showTotpDialog(player, dialog));
            helperMock.verify(() -> PaperDialogHelper.showRegisterDialog(
                player, RegistrationType.EMAIL, RegisterSecondaryArgument.CONFIRMATION, dialog));
        }
    }

    @Test
    public void teleportPlayerCallsTeleportAsync() {
        // given
        Player player = mock(Player.class, withSettings().mockMaker(MockMakers.INLINE));
        given(player.getUniqueId()).willReturn(UUID.randomUUID());
        Location location = mock(Location.class, withSettings().mockMaker(MockMakers.INLINE));
        given(player.teleportAsync(location)).willReturn(new CompletableFuture<>());

        // when
        adapter.teleportPlayer(player, location);

        // then
        verify(player).teleportAsync(location);
    }

    @Test
    public void getKickReasonReturnsPlainTextFromComponent() {
        // given
        // PlayerKickEvent is not final; reason() is a Paper API method, stubable with Mockito 5
        PlayerKickEvent event = mock(PlayerKickEvent.class, withSettings().mockMaker(MockMakers.INLINE));
        given(event.reason()).willReturn(Component.text("You were kicked"));

        // when
        String reason = adapter.getKickReason(event);

        // then
        assertThat(reason, is("You were kicked"));
    }
}

