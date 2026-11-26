package fr.xephi.authme.command.executable.totp;

import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.data.auth.PlayerCache;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.message.Messages;
import fr.xephi.authme.security.totp.GenerateTotpService;
import fr.xephi.authme.security.totp.TotpAuthenticator.TotpGenerationResult;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.Collections;
import java.util.stream.Stream;

import static fr.xephi.authme.listener.TestPlayerUtils.mockPlayerWithName;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Test for {@link AddTotpCommand}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.WARN)
public class AddTotpCommandTest {

    @InjectMocks
    private AddTotpCommand addTotpCommand;

    @Mock
    private GenerateTotpService generateTotpService;
    @Mock
    private PlayerCache playerCache;
    @Mock
    private Messages messages;

    @ParameterizedTest
    @MethodSource("invalidStates")
    public void shouldNotAddCodeForInvalidState(PlayerAuth auth, MessageKey expectedMessage) {
        // given
        Player player = mockPlayerWithName("bob");
        given(playerCache.getAuth("bob")).willReturn(auth);

        // when
        addTotpCommand.runCommand(player, Collections.emptyList());

        // then
        verify(messages).send(player, expectedMessage);
        verifyNoInteractions(generateTotpService);
    }

    private static Stream<Arguments> invalidStates() {
        return Stream.of(
            Arguments.of(null, MessageKey.NOT_LOGGED_IN),
            Arguments.of(PlayerAuth.builder().name("bob").totpKey("TOTP2345").build(),
                MessageKey.TWO_FACTOR_ALREADY_ENABLED));
    }

    @Test
    public void shouldGenerateTotpCode() {
        // given
        Player player = mockPlayerWithName("charles");
        PlayerAuth auth = PlayerAuth.builder().name("charles").build();
        given(playerCache.getAuth("charles")).willReturn(auth);

        TotpGenerationResult generationResult = new TotpGenerationResult(
            "777Key214", "http://example.org/qr-code/link");
        given(generateTotpService.generateTotpKey(player)).willReturn(generationResult);

        // when
        addTotpCommand.runCommand(player, Collections.emptyList());

        // then
        verify(messages).send(player, MessageKey.TWO_FACTOR_CREATE, generationResult.getTotpKey(), generationResult.getAuthenticatorQrCodeUrl());
        verify(messages).send(player, MessageKey.TWO_FACTOR_CREATE_CONFIRMATION_REQUIRED);
    }

}


