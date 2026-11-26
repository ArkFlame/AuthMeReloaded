package fr.xephi.authme.command.executable.email;

import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.service.CommonService;
import fr.xephi.authme.service.EmailRecoveryDialogService;
import fr.xephi.authme.service.PasswordRecoveryService;
import fr.xephi.authme.service.RecoveryCodeService;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.Collections;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Tests for {@link ProcessCodeCommand}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.WARN)
public class ProcessCodeCommandTest {

    @InjectMocks
    private ProcessCodeCommand command;

    @Mock
    private CommonService commonService;

    @Mock
    private RecoveryCodeService codeService;

    @Mock
    private PasswordRecoveryService recoveryService;

    @Mock
    private EmailRecoveryDialogService dialogRecoveryService;

    @Test
    public void shouldSendErrorForInvalidRecoveryCode() {
        String name = "Vultur3";
        Player sender = mockPlayer(name);
        given(codeService.hasTriesLeft(name)).willReturn(true);
        given(codeService.isCodeValid(name, "bogus")).willReturn(false);
        given(codeService.getTriesLeft(name)).willReturn(2);

        command.executeCommand(sender, Collections.singletonList("bogus"));

        verify(commonService).send(sender, MessageKey.INCORRECT_RECOVERY_CODE, "2");
        verifyNoMoreInteractions(recoveryService);
    }

    @Test
    public void shouldSendErrorForNoMoreTries() {
        String name = "BobbY";
        Player sender = mockPlayer(name);
        given(codeService.hasTriesLeft(name)).willReturn(false);

        command.executeCommand(sender, Collections.singletonList("bogus"));

        verify(commonService).send(sender, MessageKey.RECOVERY_TRIES_EXCEEDED);
        verify(codeService).removeCode(name);
        verifyNoMoreInteractions(recoveryService);
    }

    @Test
    public void shouldProcessCorrectCode() {
        String name = "Dwight";
        String code = "chickenDinner";
        Player sender = mockPlayer(name);
        given(codeService.hasTriesLeft(name)).willReturn(true);
        given(codeService.isCodeValid(name, code)).willReturn(true);

        command.runCommand(sender, Collections.singletonList(code));

        verify(commonService).send(sender, MessageKey.RECOVERY_CODE_CORRECT);
        verify(recoveryService).addSuccessfulRecovery(sender);
        verify(codeService).removeCode(name);
    }

    private static Player mockPlayer(String name) {
        Player player = mock(Player.class);
        given(player.getName()).willReturn(name);
        return player;
    }
}
