package fr.xephi.authme.command.executable.unregister;

import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import fr.xephi.authme.data.VerificationCodeManager;
import fr.xephi.authme.data.auth.PlayerCache;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.message.Messages;
import fr.xephi.authme.process.Management;
import fr.xephi.authme.service.CommonService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.Collections;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Test for {@link UnregisterCommand}.
 */
@ExtendWith(MockitoExtension.class)
public class UnregisterCommandTest {

    @InjectMocks
    private UnregisterCommand command;

    @Mock
    private Management management;

    @Mock
    private CommonService commonService;

    @Mock
    private PlayerCache playerCache;

    @Mock
    private VerificationCodeManager codeManager;

    @Mock
    private Messages messages;

    @Mock
    private Player player;

    @Test
    public void shouldCatchUnauthenticatedUser() {
        // given
        String password = "mySecret123";
        String name = "player77";
        given(player.getName()).willReturn(name);
        given(playerCache.isAuthenticated(name)).willReturn(false);

        // when
        command.executeCommand(player, Collections.singletonList(password));

        // then
        verify(playerCache).isAuthenticated(name);
        verify(commonService).send(player, MessageKey.NOT_LOGGED_IN);
        verifyNoInteractions(management);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void shouldStopForMissingVerificationCodeOrForwardToAsyncTask(boolean verificationRequired) {
        // given
        String password = "p@ssw0rD";
        String name = "player77";
        given(player.getName()).willReturn(name);
        given(playerCache.isAuthenticated(name)).willReturn(true);
        given(codeManager.isVerificationRequired(player)).willReturn(verificationRequired);

        // when
        command.executeCommand(player, Collections.singletonList(password));

        // then
        verify(playerCache).isAuthenticated(name);
        verify(codeManager).isVerificationRequired(player);
        if (verificationRequired) {
            verify(codeManager).codeExistOrGenerateNew(name);
            verify(commonService).send(player, MessageKey.VERIFICATION_CODE_REQUIRED);
            verifyNoInteractions(management);
        } else {
            verify(management).performUnregister(player, password);
        }
    }

    @Test
    public void shouldStopIfSenderIsNotPlayer() {
        // given
        CommandSender sender = mock(CommandSender.class);

        // when
        command.executeCommand(sender, Collections.singletonList("password"));

        // then
        verifyNoInteractions(playerCache, management);
        verify(messages).send(sender, MessageKey.PLAYER_COMMAND_ONLY_WITH_ALTERNATIVE, "/authme unregister <player>");
    }

    @Test
    public void shouldDefineArgumentMismatchMessage() {
        // given / when / then
        assertThat(command.getArgumentsMismatchMessage(), equalTo(MessageKey.USAGE_UNREGISTER));
    }
}

