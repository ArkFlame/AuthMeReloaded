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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

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
    @CsvSource({
        "true, EMAIL_CHANGED_SUCCESS, old@mail.tld, old@mail.tld, Bobby",
        "false, ERROR, old@mail.tld, old@mail.tld, Bobby",
        "true, EMAIL_CHANGED_SUCCESS, OLD-mail@example.org, old-mail@example.org, Debra"
    })
    void shouldChangeEmail(boolean saveSuccessful, MessageKey expectedMessage,
                           String storedEmail, String providedOldEmail, String playerName) {
        String newEmail = "new@mail.tld";
        PlayerAuth auth = givenAuthenticatedPlayerWithEmail(playerName, storedEmail);
        given(dataSource.updateEmail(auth)).willReturn(saveSuccessful);
        given(validationService.validateEmail(newEmail)).willReturn(true);
        given(validationService.isEmailFreeForRegistration(newEmail, player)).willReturn(true);
        given(emailService.hasAllInformation()).willReturn(false);
        EmailChangedEvent event = spy(new EmailChangedEvent(player, storedEmail, newEmail, false));
        given(bukkitService.createAndCallEvent(any(Function.class))).willReturn(event);

        process.changeEmail(player, providedOldEmail, newEmail);

        verify(dataSource).updateEmail(auth);
        verify(playerCache, saveSuccessful ? times(1) : never()).updatePlayerIfPresent(auth);
        verify(service).send(player, expectedMessage);
    }

    @Test
    void shouldShowAddEmailUsage() {
        givenAuthenticatedPlayerWithEmail("Bobby", null);

        process.changeEmail(player, "old@mail.tld", "new@mailt.tld");

        verifyEmailNotUpdated();
        verify(service).send(player, MessageKey.USAGE_ADD_EMAIL);
    }

    @ParameterizedTest(name = "newEmailValid={2}")
    @CsvSource({
        "bogus, old@mail.tld, false, INVALID_NEW_EMAIL",
        "new@mail.tld, other@address.email, true, INVALID_OLD_EMAIL"
    })
    void shouldRejectInvalidEmails(String newEmail, String storedEmail, boolean newEmailValid,
                                   MessageKey expectedMessage) {
        givenAuthenticatedPlayerWithEmail("Bobby", storedEmail);
        given(validationService.validateEmail(newEmail)).willReturn(newEmailValid);

        process.changeEmail(player, "old@mail.tld", newEmail);

        verifyEmailNotUpdated();
        verify(service).send(player, expectedMessage);
    }

    @Test
    void shouldRejectAlreadyUsedEmail() {
        String newEmail = "new@example.com";
        givenAuthenticatedPlayerWithEmail("Username", "old@example.com");
        given(validationService.validateEmail(newEmail)).willReturn(true);
        given(validationService.isEmailFreeForRegistration(newEmail, player)).willReturn(false);

        process.changeEmail(player, "old@example.com", newEmail);

        verifyEmailNotUpdated();
        verify(service).send(player, MessageKey.EMAIL_ALREADY_USED_ERROR);
    }

    @ParameterizedTest(name = "authenticated={0}, registered={1}")
    @CsvSource({
        "false, true, LOGIN_MESSAGE",
        "false, false, REGISTER_MESSAGE",
        "true, true, LOGIN_MESSAGE"
    })
    void shouldShowLoginOrRegisterMessageForUnauthenticatedPlayer(boolean authenticated, boolean isRegistered,
                                                                  MessageKey expectedMessage) {
        given(player.getName()).willReturn("Bobby");
        given(playerCache.isAuthenticated("bobby")).willReturn(authenticated);
        given(dataSource.isAuthAvailable("Bobby")).willReturn(isRegistered);

        process.changeEmail(player, "old@mail.tld", "new@mail.tld");

        verifyEmailNotUpdated();
        verify(service).send(player, expectedMessage);
    }

    @Test
    void shouldNotChangeOnCancelledEvent() {
        String newEmail = "new@example.com";
        String oldEmail = "old@example.com";
        givenAuthenticatedPlayerWithEmail("Username", oldEmail);
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
        PlayerAuth auth = mock(PlayerAuth.class);
        given(auth.getEmail()).willReturn(email);
        given(playerCache.getAuth(name.toLowerCase())).willReturn(auth);
        return auth;
    }

    private void verifyEmailNotUpdated() {
        verify(dataSource, never()).updateEmail(any(PlayerAuth.class));
        verify(playerCache, never()).updatePlayerIfPresent(any(PlayerAuth.class));
    }

}
