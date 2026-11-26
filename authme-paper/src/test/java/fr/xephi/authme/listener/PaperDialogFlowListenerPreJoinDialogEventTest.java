package fr.xephi.authme.listener;

import ch.jalu.configme.properties.Property;
import com.destroystokyo.paper.profile.PlayerProfile;
import fr.xephi.authme.events.PreJoinDialogEvent;
import fr.xephi.authme.platform.PaperDialogHelper;
import fr.xephi.authme.service.BukkitService;
import fr.xephi.authme.service.CommonService;
import fr.xephi.authme.service.DialogWindowService;
import fr.xephi.authme.service.PreJoinDialogService;
import fr.xephi.authme.data.ProxySessionManager;
import fr.xephi.authme.settings.properties.RestrictionSettings;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.dialog.Dialog;
import net.kyori.adventure.audience.Audience;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** Verifies that the pre-join dialog event is fired before the dialog and honours cancellation. */
public class PaperDialogFlowListenerPreJoinDialogEventTest {

    private static final long SESSION_ID = 42L;

    @Test
    public void shouldFireLoginEventOnceBeforeShowingDialog() throws Exception {
        Fixture fixture = new Fixture();
        fixture.givenTimeout(RestrictionSettings.LOGIN_TIMEOUT, 0);
        fixture.answerDialogWithResponse("pendingLoginResponses");

        try (var helperStatic = mockStatic(PaperDialogHelper.class)) {
            helperStatic.when(() -> PaperDialogHelper.createPreJoinLoginDialog(any())).thenReturn(null);
            invokeBlockingMethod(fixture, "handleBlockingLoginDialog",
                PlayerConfigurationConnection.class, long.class, String.class);
        }

        assertThat(fixture.events.size(), is(1));
        PreJoinDialogEvent event = fixture.events.get(0);
        assertThat(event.getDialogType(), is(PreJoinDialogEvent.DialogType.LOGIN));
        assertThat(event.isRegistered(), is(true));
        assertThat(event.getPlayerName(), is("Bobby"));
        assertThat(event.getPlayerId(), is(fixture.playerId));
        assertThat(event.getAddress(), is(fixture.address));
        assertThat(event.isAsynchronous(), is(true));
        verify(fixture.bukkitService, times(1)).callEvent(any(PreJoinDialogEvent.class));
        verify(fixture.audience).showDialog(any());
    }

    @Test
    public void shouldFireRegisterEventOnceBeforeShowingDialog() throws Exception {
        Fixture fixture = new Fixture();
        fixture.givenTimeout(RestrictionSettings.REGISTER_TIMEOUT, 0);
        fixture.answerDialogWithResponse("pendingRegisterResponses");

        invokeBlockingMethod(fixture, "handleBlockingRegisterDialog",
            PlayerConfigurationConnection.class, long.class, String.class, Dialog.class);

        assertThat(fixture.events.size(), is(1));
        PreJoinDialogEvent event = fixture.events.get(0);
        assertThat(event.getDialogType(), is(PreJoinDialogEvent.DialogType.REGISTER));
        assertThat(event.isRegistered(), is(false));
        verify(fixture.bukkitService, times(1)).callEvent(any(PreJoinDialogEvent.class));
        verify(fixture.audience).showDialog(any());
    }

    @Test
    public void shouldSkipDialogWhenCancelledWithoutAuthenticating() throws Exception {
        Fixture fixture = new Fixture();
        fixture.givenTimeout(RestrictionSettings.LOGIN_TIMEOUT, 0);
        willAnswer(invocation -> {
            PreJoinDialogEvent event = invocation.getArgument(0);
            fixture.events.add(event);
            event.setCancelled(true);
            return null;
        }).given(fixture.bukkitService).callEvent(any(PreJoinDialogEvent.class));

        try (var helperStatic = mockStatic(PaperDialogHelper.class)) {
            helperStatic.when(() -> PaperDialogHelper.createPreJoinLoginDialog(any())).thenReturn(null);
            invokeBlockingMethod(fixture, "handleBlockingLoginDialog",
                PlayerConfigurationConnection.class, long.class, String.class);
        }

        assertThat(fixture.events.size(), is(1));
        assertThat(fixture.events.get(0).isCancelled(), is(true));
        verify(fixture.audience, never()).showDialog(any());
        verify(fixture.preJoinDialogService, never()).markSkipPostJoinDialog(anyLong());
        verify(fixture.preJoinDialogService, never()).storePendingLoginPassword(anyLong(), any());
    }

    @Test
    public void shouldStillShowDialogWhenListenerThrows() throws Exception {
        Fixture fixture = new Fixture();
        fixture.givenTimeout(RestrictionSettings.LOGIN_TIMEOUT, 0);
        fixture.answerDialogWithResponse("pendingLoginResponses");
        willThrow(new RuntimeException("bad listener")).given(fixture.bukkitService)
            .callEvent(any(PreJoinDialogEvent.class));

        try (var helperStatic = mockStatic(PaperDialogHelper.class)) {
            helperStatic.when(() -> PaperDialogHelper.createPreJoinLoginDialog(any())).thenReturn(null);
            invokeBlockingMethod(fixture, "handleBlockingLoginDialog",
                PlayerConfigurationConnection.class, long.class, String.class);
        }

        verify(fixture.bukkitService).callEvent(any(PreJoinDialogEvent.class));
        verify(fixture.audience).showDialog(any());
    }

    private static void invokeBlockingMethod(Fixture fixture, String name,
                                             Class<?>... parameterTypes) throws Exception {
        Method method = PaperDialogFlowListener.class.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        if (parameterTypes.length == 3) {
            method.invoke(fixture.listener, fixture.connection, SESSION_ID, "Bobby");
        } else {
            method.invoke(fixture.listener, fixture.connection, SESSION_ID, "Bobby", null);
        }
    }

    private static final class Fixture {
        final PaperDialogFlowListener listener = new PaperDialogFlowListener();
        final BukkitService bukkitService = mock(BukkitService.class);
        final CommonService commonService = mock(CommonService.class);
        final PreJoinDialogService preJoinDialogService = mock(PreJoinDialogService.class);
        final ProxySessionManager proxySessionManager = mock(ProxySessionManager.class);
        final DialogWindowService dialogWindowService = mock(DialogWindowService.class);
        final Audience audience = mock(Audience.class);
        final UUID playerId = UUID.fromString("f84c6a80-9f2a-4a9c-8a90-3f4ccde7d0b2");
        final InetAddress address = mock(InetAddress.class);
        final PlayerConfigurationConnection connection;
        final List<PreJoinDialogEvent> events = new ArrayList<>();

        Fixture() throws Exception {
            setField(listener, "bukkitService", bukkitService);
            setField(listener, "commonService", commonService);
            setField(listener, "preJoinDialogService", preJoinDialogService);
            setField(listener, "proxySessionManager", proxySessionManager);
            setField(listener, "dialogWindowService", dialogWindowService);

            PlayerProfile profile = mock(PlayerProfile.class);
            given(profile.getName()).willReturn("Bobby");
            given(profile.getId()).willReturn(playerId);
            connection = mock(PlayerConfigurationConnection.class);
            given(connection.getProfile()).willReturn(profile);
            given(connection.getAudience()).willReturn(audience);
            given(connection.getClientAddress()).willReturn(new InetSocketAddress(address, 25565));

            willAnswer(invocation -> {
                events.add(invocation.getArgument(0));
                return null;
            }).given(bukkitService).callEvent(any(PreJoinDialogEvent.class));
        }

        void givenTimeout(Property<Integer> property, int seconds) throws Exception {
            given(commonService.getProperty(property)).willReturn(seconds);
        }

        void answerDialogWithResponse(String responseField) {
            willAnswer(invocation -> {
                CompletableFuture<String> future = responses(responseField).get(SESSION_ID);
                if (future != null) {
                    future.complete(null);
                }
                return null;
            }).given(audience).showDialog(any());
        }

        @SuppressWarnings("unchecked")
        private ConcurrentMap<Long, CompletableFuture<String>> responses(String responseField) throws Exception {
            Field field = PaperDialogFlowListener.class.getDeclaredField(responseField);
            field.setAccessible(true);
            return (ConcurrentMap<Long, CompletableFuture<String>>) field.get(listener);
        }
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
