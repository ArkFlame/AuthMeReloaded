package fr.xephi.authme.events;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.Mockito.mock;

class PreJoinDialogEventTest {

    private final UUID playerId = UUID.fromString("f84c6a80-9f2a-4a9c-8a90-3f4ccde7d0b2");
    private final InetAddress address = mock(InetAddress.class);

    @Test
    void shouldExposeIdentityAndDialogType() {
        PreJoinDialogEvent event = new PreJoinDialogEvent("Bobby", playerId, address,
            PreJoinDialogEvent.DialogType.REGISTER, false, true);

        assertThat(event.getPlayerName(), equalTo("Bobby"));
        assertThat(event.getPlayerId(), equalTo(playerId));
        assertThat(event.getUuid(), equalTo(playerId));
        assertThat(event.getAddress(), equalTo(address));
        assertThat(event.getInetAddress(), equalTo(address));
        assertThat(event.getDialogType(), equalTo(PreJoinDialogEvent.DialogType.REGISTER));
        assertThat(event.getType(), equalTo(PreJoinDialogEvent.DialogType.REGISTER));
        assertThat(event.isRegistered(), equalTo(false));
        assertThat(event.isAsynchronous(), equalTo(true));
        assertThat(event.getHandlers() == PreJoinDialogEvent.getHandlerList(), equalTo(true));
        assertThat(event.isCancelled(), equalTo(false));
    }

    @Test
    void shouldAllowMissingAddress() {
        PreJoinDialogEvent event = new PreJoinDialogEvent("Bobby", playerId,
            PreJoinDialogEvent.DialogType.LOGIN, true, true);

        assertThat(event.getAddress(), nullValue());
        assertThat(event.getDialogType(), equalTo(PreJoinDialogEvent.DialogType.LOGIN));
        assertThat(event.isRegistered(), equalTo(true));
    }

    @Test
    void shouldAllowMissingPlayerId() {
        PreJoinDialogEvent event = new PreJoinDialogEvent("Bobby", null, address,
            PreJoinDialogEvent.DialogType.LOGIN, true, true);

        assertThat(event.getPlayerId(), nullValue());
        assertThat(event.getUuid(), nullValue());
        assertThat(event.getPlayerName(), equalTo("Bobby"));
        assertThat(event.getAddress(), sameInstance(address));
        assertThat(event.getDialogType(), equalTo(PreJoinDialogEvent.DialogType.LOGIN));
    }

    @Test
    void shouldToggleCancellation() {
        PreJoinDialogEvent event = new PreJoinDialogEvent("Bobby", playerId, address,
            PreJoinDialogEvent.DialogType.LOGIN, true, true);

        event.setCancelled(true);
        assertThat(event.isCancelled(), equalTo(true));

        event.setCancelled(false);
        assertThat(event.isCancelled(), equalTo(false));
    }

    @Test
    void shouldOfferLoginAndRegisterDialogTypes() {
        assertThat(PreJoinDialogEvent.DialogType.values(),
            arrayContaining(PreJoinDialogEvent.DialogType.LOGIN, PreJoinDialogEvent.DialogType.REGISTER));
    }
}
