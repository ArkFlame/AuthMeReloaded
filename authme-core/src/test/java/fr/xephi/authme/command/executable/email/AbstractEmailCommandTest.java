package fr.xephi.authme.command.executable.email;

import fr.xephi.authme.command.ExecutableCommand;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.message.Messages;
import fr.xephi.authme.process.Management;
import fr.xephi.authme.service.CommonService;
import fr.xephi.authme.service.ValidationService;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collections;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Shared tests for the email commands that are restricted to players.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.WARN)
abstract class AbstractEmailCommandTest {

    @Mock
    protected CommonService commonService;
    @Mock
    protected Management management;
    @Mock
    protected ValidationService validationService;
    @Mock
    protected Messages messages;

    @Test
    void shouldRejectNonPlayerSender() {
        // given
        CommandSender sender = mock(BlockCommandSender.class);

        // when
        getCommand().executeCommand(sender, Collections.emptyList());

        // then
        verifyNoInteractions(management);
    }

    @Test
    void shouldDefineArgumentMismatchMessage() {
        // given / when / then
        assertThat(getCommand().getArgumentsMismatchMessage(), equalTo(getUsageMessage()));
    }

    /** @return the command under test */
    protected abstract ExecutableCommand getCommand();

    /** @return the usage message the command defines for an argument mismatch */
    protected abstract MessageKey getUsageMessage();
}
