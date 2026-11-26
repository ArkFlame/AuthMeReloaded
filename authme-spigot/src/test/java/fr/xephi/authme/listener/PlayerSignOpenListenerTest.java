package fr.xephi.authme.listener;

import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerSignOpenEvent;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.anyBoolean;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.WARN)
public class PlayerSignOpenListenerTest {

    @InjectMocks
    private PlayerSignOpenListener listener;

    @Mock
    private ListenerService listenerService;

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void shouldCancelEventOnlyForUnauthenticatedPlayer(boolean shouldCancel) {
        // given
        // PlayerSignOpenEvent#getPlayer is final (PlayerEvent), so use spy on a real instance
        Player player = mock(Player.class);
        PlayerSignOpenEvent event = spy(new PlayerSignOpenEvent(
            player, mock(Sign.class), Side.FRONT, PlayerSignOpenEvent.Cause.UNKNOWN));
        given(listenerService.shouldCancelEvent(player)).willReturn(shouldCancel);

        // when
        listener.onPlayerSignOpen(event);

        // then
        if (shouldCancel) {
            verify(event).setCancelled(true);
        } else {
            verify(event, never()).setCancelled(anyBoolean());
        }
    }
}
