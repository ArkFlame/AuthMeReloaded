package fr.xephi.authme.process.email;

import fr.xephi.authme.TestHelper;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.data.auth.PlayerCache;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.events.EmailChangedEvent;
import fr.xephi.authme.mail.EmailService;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.service.BukkitService;
import fr.xephi.authme.service.CommonService;
import fr.xephi.authme.service.ValidationService;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.function.Function;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test for {@link AsyncChangeEmail}.
 */
@ExtendWith(MockitoExtension.class)
class AsyncChangeEmailTest {

    @InjectMocks
    private AsyncChangeEmail process;

    @Mock
    private Player player;

    @Mock
    private PlayerCache playerCache;

    @Mock
    private DataSource dataSource;

    @Mock
    private CommonService service;

    @Mock
    private ValidationService validationService;


    @Mock
    private BukkitService bukkitService;

    @Mock
    private EmailService emailService;

    @BeforeAll
    static void setUp() {
        TestHelper.setupLogger();
    }

    @ParameterizedTest(name = "save successful={0}")
    @CsvSource({"true, EMAIL_CHANGED_SUCCESS", "false, ERROR"})
    void shouldChangeEmail(boolean saveSuccessful, MessageKey expectedMessage) {
        String newEmail = "new@mail.tld";
        PlayerAuth auth = givenAuthenticatedPlayerWithEmail("Bobby", "old@mail.tld");
        given(dataSource.updateEmail(auth)).willReturn(saveSuccessful);
        given(validationService.validateEmail(newEmail)).willReturn(true);
        given(validationService.isEmailFreeForRegistration(newEmail, player)).willReturn(true);
        given(emailService.hasAllInformation()).willReturn(false);
        EmailChangedEvent event = spy(new EmailChangedEvent(player, "old@mail.tld", newEmail, false));
        given(bukkitService.createAndCallEvent(any(Function.class))).willReturn(event);

        process.changeEmail(player, "old@mail.tld", newEmail);

        verify(dataSource).updateEmail(auth);
        if (saveSuccessful) {
            verify(playerCache).updatePlayerIfPresent(auth);
        } else {
            verify(playerCache, never()).updatePlayerIfPresent(auth);
        }
        verify(service).send(player, expectedMessage);
    }

    @Test
    void shouldNotBeCaseSensitiveWhenComparingEmails() {
        String newEmail = "newmail@example.com";
        given(player.getName()).willReturn("Debra");
        given(playerCache.isAuthenticated("debra")).willReturn(true);
        String oldEmail = "OLD-mail@example.org";
        PlayerAuth auth = authWithMail(oldEmail);
        given(playerCache.getAuth("debra")).willReturn(auth);
        given(dataSource.updateEmail(auth)).willReturn(true);
        given(validationService.validateEmail(newEmail)).willReturn(true);
        given(validationService.isEmailFreeForRegistration(newEmail, player)).willReturn(true);
        given(emailService.hasAllInformation()).willReturn(false);
        EmailChangedEvent event = spy(new EmailChangedEvent(player, oldEmail, newEmail, false));
        given(bukkitService.createAndCallEvent(any(Function.class))).willReturn(event);

        process.changeEmail(player, "old-mail@example.org", newEmail);

        verify(dataSource).updateEmail(auth);
        verify(playerCache).updatePlayerIfPresent(auth);
        verify(service).send(player, MessageKey.EMAIL_CHANGED_SUCCESS);
    }

    @Test
    void shouldShowAddEmailUsage() {
        given(player.getName()).willReturn("Bobby");
        given(playerCache.isAuthenticated("bobby")).willReturn(true);
        PlayerAuth auth = authWithMail(null);
        given(playerCache.getAuth("bobby")).willReturn(auth);

        process.changeEmail(player, "old@mail.tld", "new@mailt.tld");

        verifyEmailNotUpdated();
        verify(service).send(player, MessageKey.USAGE_ADD_EMAIL);
    }

    @Test
    void shouldRejectInvalidNewMail() {
        String newEmail = "bogus";
        PlayerAuth auth = givenAuthenticatedPlayerWithEmail("Bobby", "old@mail.tld");
        given(validationService.validateEmail(newEmail)).willReturn(false);

        process.changeEmail(player, "old@mail.tld", newEmail);

        verifyEmailNotUpdated();
        verify(service).send(player, MessageKey.INVALID_NEW_EMAIL);
    }

    @Test
    void shouldRejectInvalidOldEmail() {
        String newEmail = "new@mail.tld";
        given(player.getName()).willReturn("Bobby");
        given(playerCache.isAuthenticated("bobby")).willReturn(true);
        PlayerAuth auth = authWithMail("other@address.email");
        given(playerCache.getAuth("bobby")).willReturn(auth);
        given(validationService.validateEmail(newEmail)).willReturn(true);

        process.changeEmail(player, "old@mail.tld", newEmail);

        verifyEmailNotUpdated();
        verify(service).send(player, MessageKey.INVALID_OLD_EMAIL);
    }

    @Test
    void shouldRejectAlreadyUsedEmail() {
        String newEmail = "new@example.com";
        given(player.getName()).willReturn("Username");
        given(playerCache.isAuthenticated("username")).willReturn(true);
        PlayerAuth auth = authWithMail("old@example.com");
        given(playerCache.getAuth("username")).willReturn(auth);
        given(validationService.validateEmail(newEmail)).willReturn(true);
        given(validationService.isEmailFreeForRegistration(newEmail, player)).willReturn(false);

        process.changeEmail(player, "old@example.com", newEmail);

        verifyEmailNotUpdated();
        verify(service).send(player, MessageKey.EMAIL_ALREADY_USED_ERROR);
    }

    @ParameterizedTest(name = "registered={0}")
    @CsvSource({"true, LOGIN_MESSAGE", "false, REGISTER_MESSAGE"})
    void shouldShowLoginOrRegisterMessageForUnauthenticatedPlayer(boolean isRegistered, MessageKey expectedMessage) {
        given(player.getName()).willReturn("Bobby");
        given(playerCache.isAuthenticated("bobby")).willReturn(false);
        given(dataSource.isAuthAvailable("Bobby")).willReturn(isRegistered);

        process.changeEmail(player, "old@mail.tld", "new@mail.tld");

        verifyEmailNotUpdated();
        verify(service).send(player, expectedMessage);
    }

    @Test
    void shouldNotChangeOnCancelledEvent() {
        String newEmail = "new@example.com";
        String oldEmail = "old@example.com";
        given(player.getName()).willReturn("Username");
        given(playerCache.isAuthenticated("username")).willReturn(true);
        PlayerAuth auth = authWithMail(oldEmail);
        given(playerCache.getAuth("username")).willReturn(auth);
        given(validationService.validateEmail(newEmail)).willReturn(true);
        given(validationService.isEmailFreeForRegistration(newEmail, player)).willReturn(true);
        EmailChangedEvent event = spy(new EmailChangedEvent(player, oldEmail, newEmail, false));
        event.setCancelled(true);
        given(bukkitService.createAndCallEvent(any(Function.class))).willReturn(event);

        process.changeEmail(player, oldEmail, newEmail);

        verifyEmailNotUpdated();
        verify(service).send(player, MessageKey.EMAIL_CHANGE_NOT_ALLOWED);
    }

    private PlayerAuth givenAuthenticatedPlayerWithEmail(String name, String email) {
        given(player.getName()).willReturn(name);
        given(playerCache.isAuthenticated(name.toLowerCase())).willReturn(true);
        PlayerAuth auth = authWithMail(email);
        given(playerCache.getAuth(name.toLowerCase())).willReturn(auth);
        return auth;
    }

    private void verifyEmailNotUpdated() {
        verify(dataSource, never()).updateEmail(any(PlayerAuth.class));
        verify(playerCache, never()).updatePlayerIfPresent(any(PlayerAuth.class));
    }

    private static PlayerAuth authWithMail(String email) {
        PlayerAuth auth = mock(PlayerAuth.class);
        when(auth.getEmail()).thenReturn(email);
        return auth;
    }

}
