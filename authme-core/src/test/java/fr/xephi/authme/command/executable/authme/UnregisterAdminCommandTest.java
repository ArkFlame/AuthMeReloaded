package fr.xephi.authme.command.executable.authme;

import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.process.Management;
import fr.xephi.authme.service.BukkitService;
import fr.xephi.authme.service.CommonService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.verify;

/**
 * Test for {@link UnregisterAdminCommand}.
 */
@ExtendWith(MockitoExtension.class)
class UnregisterAdminCommandTest {

    private static final String USER = "bobby";

    @InjectMocks
    private UnregisterAdminCommand command;

    @Mock
    private DataSource dataSource;

    @Mock
    private CommonService commandService;

    @Mock
    private BukkitService bukkitService;

    @Mock
    private Management management;

    @Mock
    private CommandSender sender;

    @ParameterizedTest(name = "user exists: {0}, player online: {1}")
    @CsvSource({"false, false", "true, true", "true, false"})
    void shouldUnregisterUserOrReportUnknown(boolean userExists, boolean playerOnline) {
        // given
        given(dataSource.isAuthAvailable(USER)).willReturn(userExists);
        Player player = userExists && playerOnline ? mock(Player.class) : null;
        if (userExists) {
            given(bukkitService.getPlayerExact(USER)).willReturn(player);
        }

        // when
        command.executeCommand(sender, Collections.singletonList(USER));

        // then
        verify(dataSource, only()).isAuthAvailable(USER);
        if (userExists) {
            verify(bukkitService).getPlayerExact(USER);
            verify(management).performUnregisterByAdmin(sender, USER, player);
        } else {
            verify(commandService).send(sender, MessageKey.UNKNOWN_USER);
        }
    }
}
