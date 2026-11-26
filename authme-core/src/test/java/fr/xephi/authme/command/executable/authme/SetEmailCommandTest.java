package fr.xephi.authme.command.executable.authme;

import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.data.auth.PlayerCache;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.service.BukkitService;
import fr.xephi.authme.service.CommonService;
import fr.xephi.authme.service.ValidationService;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;

import static fr.xephi.authme.service.BukkitServiceTestHelper.setBukkitServiceToRunTaskOptionallyAsync;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Test for {@link SetEmailCommand}.
 */
@ExtendWith(MockitoExtension.class)
class SetEmailCommandTest {

    @InjectMocks
    private SetEmailCommand command;

    @Mock
    private DataSource dataSource;

    @Mock
    private CommonService commandService;

    @Mock
    private PlayerCache playerCache;

    @Mock
    private BukkitService bukkitService;

    @Mock
    private ValidationService validationService;

    private final CommandSender sender = mock(CommandSender.class);

    @Test
    void shouldRejectInvalidMail() {
        // given
        String user = "somebody";
        String email = "some.test@example.org";
        given(validationService.validateEmail(email)).willReturn(false);

        // when
        command.executeCommand(sender, Arrays.asList(user, email));

        // then
        verify(validationService).validateEmail(email);
        verify(commandService).send(sender, MessageKey.INVALID_EMAIL);
        verifyNoInteractions(dataSource);
    }

    @Test
    void shouldHandleUnknownUser() {
        // given
        String user = "nonexistent";
        String email = "mail@example.com";
        given(validationService.validateEmail(email)).willReturn(true);
        given(dataSource.getAuth(user)).willReturn(null);
        setBukkitServiceToRunTaskOptionallyAsync(bukkitService);

        // when
        command.executeCommand(sender, Arrays.asList(user, email));

        // then
        verify(validationService).validateEmail(email);
        verify(dataSource).getAuth(user);
        verify(commandService).send(sender, MessageKey.UNKNOWN_USER);
        verifyNoMoreInteractions(dataSource);
    }

    @Test
    void shouldHandleAlreadyTakenEmail() {
        // given
        String user = "someone";
        String email = "mail@example.com";
        given(validationService.validateEmail(email)).willReturn(true);
        PlayerAuth auth = mock(PlayerAuth.class);
        given(dataSource.getAuth(user)).willReturn(auth);
        given(validationService.isEmailFreeForRegistration(email, sender)).willReturn(false);
        setBukkitServiceToRunTaskOptionallyAsync(bukkitService);

        // when
        command.executeCommand(sender, Arrays.asList(user, email));

        // then
        verify(validationService).validateEmail(email);
        verify(dataSource).getAuth(user);
        verify(validationService).isEmailFreeForRegistration(email, sender);
        verify(commandService).send(sender, MessageKey.EMAIL_ALREADY_USED_ERROR);
        verifyNoMoreInteractions(dataSource);
        verifyNoInteractions(auth);
    }

    @ParameterizedTest
    @CsvSource({"false, ERROR", "true, EMAIL_CHANGED_SUCCESS"})
    void shouldHandleEmailUpdateResult(boolean updateSuccessful, MessageKey expectedMessage) {
        // given
        String user = "Bobby";
        String email = "new-addr@example.org";
        given(validationService.validateEmail(email)).willReturn(true);
        PlayerAuth auth = mock(PlayerAuth.class);
        given(dataSource.getAuth(user)).willReturn(auth);
        given(validationService.isEmailFreeForRegistration(email, sender)).willReturn(true);
        given(dataSource.updateEmail(auth)).willReturn(updateSuccessful);
        setBukkitServiceToRunTaskOptionallyAsync(bukkitService);

        // when
        command.executeCommand(sender, Arrays.asList(user, email));

        // then
        verify(validationService).validateEmail(email);
        verify(dataSource).getAuth(user);
        verify(validationService).isEmailFreeForRegistration(email, sender);
        verify(commandService).send(sender, expectedMessage);
        verify(dataSource).updateEmail(auth);
        if (updateSuccessful) {
            verify(playerCache).updatePlayerIfPresent(auth);
        }
        verifyNoMoreInteractions(dataSource);
    }

}
