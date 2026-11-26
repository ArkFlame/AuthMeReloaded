package fr.xephi.authme.command.executable.authme;

import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.service.BukkitService;
import fr.xephi.authme.task.purge.PurgeExecutor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Locale;

import static fr.xephi.authme.service.BukkitServiceTestHelper.setBukkitServiceToRunTaskAsynchronously;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.hamcrest.MockitoHamcrest.argThat;

/**
 * Test for {@link PurgePlayerCommand}.
 */
@ExtendWith(MockitoExtension.class)
class PurgePlayerCommandTest {

    @InjectMocks
    private PurgePlayerCommand command;

    @Mock
    private BukkitService bukkitService;

    @Mock
    private PurgeExecutor purgeExecutor;

    @Mock
    private DataSource dataSource;

    @Test
    void shouldNotExecutePurgeForRegisteredPlayer() {
        // given
        String name = "Bobby";
        given(dataSource.isAuthAvailable(name)).willReturn(true);

        // when
        CommandSender sender = executeCommand(name);

        // then
        verify(sender).sendMessage(argThat(containsString("This player is still registered")));
        verifyNoInteractions(purgeExecutor);
    }

    @ParameterizedTest
    @CsvSource({"Frank, false", "GhiJKlmn7, true"})
    void shouldExecutePurge(String name, boolean force) {
        // given
        OfflinePlayer player = mock(OfflinePlayer.class);
        given(bukkitService.getOfflinePlayer(name)).willReturn(player);
        if (!force) {
            given(dataSource.isAuthAvailable(name)).willReturn(false);
        }

        // when
        executeCommand(force ? new String[]{name, "force"} : new String[]{name});

        // then
        if (!force) {
            verify(dataSource).isAuthAvailable(name);
        }
        verify(purgeExecutor).executePurge(singletonList(player), singletonList(name.toLowerCase(Locale.ROOT)));
    }

    private CommandSender executeCommand(String... args) {
        CommandSender sender = mock(CommandSender.class);
        setBukkitServiceToRunTaskAsynchronously(bukkitService);
        command.executeCommand(sender, asList(args));
        return sender;
    }
}
