package fr.xephi.authme.command.executable.authme;

import fr.xephi.authme.TestHelper;
import fr.xephi.authme.command.ExecutableCommand;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.message.Messages;
import fr.xephi.authme.service.BukkitService;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.util.Collections;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.verify;

/** Shared tests for admin commands that look up a user's two-factor authentication status. */
abstract class AbstractTotpAdminCommandTest {

    @Mock
    protected DataSource dataSource;
    @Mock
    protected Messages messages;
    @Mock
    protected BukkitService bukkitService;

    @BeforeAll
    static void initLogger() {
        TestHelper.setupLogger();
    }

    @BeforeEach
    void runTasksImmediately() {
        fr.xephi.authme.service.BukkitServiceTestHelper.setBukkitServiceToRunTaskOptionallyAsync(bukkitService);
    }

    @Test
    void shouldHandleUnknownUser() {
        CommandSender sender = mock(CommandSender.class);
        getCommand().executeCommand(sender, Collections.singletonList("user"));

        verify(messages).send(sender, MessageKey.UNKNOWN_USER);
        verify(dataSource, only()).getAuth("user");
    }

    /** @return the command under test */
    protected abstract ExecutableCommand getCommand();
}
