package fr.xephi.authme.listener;

import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.message.Messages;
import fr.xephi.authme.service.ValidationService;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerLoginEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockMakers;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.stream.Stream;

import static fr.xephi.authme.listener.TestPlayerUtils.mockPlayerWithName;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class LegacyPlayerLoginListenerTest {

    @InjectMocks
    private LegacyPlayerLoginListener listener;

    @Mock
    private Messages messages;
    @Mock
    private OnJoinVerifier onJoinVerifier;
    @Mock
    private ValidationService validationService;

    @ParameterizedTest(name = "unrestricted={0}, fullServerRefusal={1}, alreadyRejected={2}")
    @MethodSource("loginScenarios")
    void shouldHandleLogin(boolean unrestricted, boolean refuseForFullServer,
                           boolean alreadyRejected, String name) throws FailedVerificationException {
        Player player = mockPlayerWithName(name);
        PlayerLoginEvent rawEvent = new PlayerLoginEvent(player, "", null);
        if (alreadyRejected) {
            rawEvent.setResult(PlayerLoginEvent.Result.KICK_BANNED);
        }
        PlayerLoginEvent event = inlineSpy(rawEvent);

        given(validationService.isUnrestricted(name)).willReturn(unrestricted);
        if (!unrestricted) {
            given(onJoinVerifier.refusePlayerForFullServer(event)).willReturn(refuseForFullServer);
        }

        listener.onPlayerLogin(event);

        verify(validationService).isUnrestricted(name);
        verify(onJoinVerifier).checkSingleSession(name);
        if (!unrestricted) {
            verify(onJoinVerifier).refusePlayerForFullServer(event);
        }
        verifyNoMoreInteractions(onJoinVerifier);
        verifyNoModifyingCalls(event);
    }

    private static Stream<Arguments> loginScenarios() {
        return Stream.of(
            Arguments.of(true, false, false, "Player01"),
            Arguments.of(false, true, false, "someone"),
            Arguments.of(false, false, true, "someone"),
            Arguments.of(false, false, false, "someone"));
    }

    @Test
    void shouldKickForFailedSingleSessionCheck() throws FailedVerificationException {
        String name = "someone";
        Player player = mockPlayerWithName(name);
        PlayerLoginEvent event = inlineSpy(new PlayerLoginEvent(player, "", null));
        FailedVerificationException exception = new FailedVerificationException(MessageKey.ALREADY_LOGGED_IN_ERROR);
        doThrow(exception).when(onJoinVerifier).checkSingleSession(name);
        given(messages.retrieveSingle(name, exception.getReason(), exception.getArgs())).willReturn("Already logged in");

        listener.onPlayerLogin(event);

        verify(event).setKickMessage("Already logged in");
        verify(event).setResult(PlayerLoginEvent.Result.KICK_OTHER);
        verifyNoInteractions(validationService);
    }

    private static void verifyNoModifyingCalls(PlayerLoginEvent event) {
        verify(event, atLeast(0)).getPlayer();
        verify(event, atLeast(0)).getResult();
        verify(event, atLeast(0)).getAddress();
        verifyNoMoreInteractions(event);
    }

    @SuppressWarnings("unchecked")
    private static <T> T inlineSpy(T instance) {
        return mock((Class<T>) instance.getClass(), withSettings().spiedInstance(instance)
            .defaultAnswer(CALLS_REAL_METHODS).mockMaker(MockMakers.INLINE));
    }
}
