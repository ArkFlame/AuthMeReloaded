package fr.xephi.authme.process.email;

import fr.xephi.authme.TestHelper;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.data.auth.PlayerCache;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.events.EmailChangedEvent;
import fr.xephi.authme.mail.EmailService;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.process.register.RegisterSecondaryArgument;
import fr.xephi.authme.settings.properties.RegistrationSettings;
import fr.xephi.authme.service.BukkitService;
import fr.xephi.authme.service.CommonService;
import fr.xephi.authme.service.PendingEmailVerificationCache;
import fr.xephi.authme.service.ValidationService;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.function.Function;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * Test for {@link AsyncAddEmail}.
 */
@ExtendWith(MockitoExtension.class)
class AsyncAddEmailTest {

    @InjectMocks
    private AsyncAddEmail asyncAddEmail;

    @Mock
    private Player player;

    @Mock
    private DataSource dataSource;

    @Mock
    private PlayerCache playerCache;

    @Mock
    private CommonService service;

    @Mock
    private ValidationService validationService;


    @Mock
    private BukkitService bukkitService;

    @Mock
    private EmailService emailService;

    @Mock
    private PendingEmailVerificationCache pendingEmailVerificationCache;

    @BeforeAll
    static void setUp() {
        TestHelper.setupLogger();
    }

    private PlayerAuth givenAuthenticatedPlayerWithoutEmail(String name) {
        String normalized = name.toLowerCase();
        given(player.getName()).willReturn(name);
        given(playerCache.isAuthenticated(normalized)).willReturn(true);
        PlayerAuth auth = mock(PlayerAuth.class);
        given(auth.getEmail()).willReturn(null);
        given(playerCache.getAuth(normalized)).willReturn(auth);
        return auth;
    }

    private EmailChangedEvent givenEmailChangedEvent(String email) {
        EmailChangedEvent event = spy(new EmailChangedEvent(player, null, email, false));
        given(bukkitService.createAndCallEvent(any(Function.class))).willReturn(event);
        return event;
    }

    @ParameterizedTest(name = "save successful={0}")
    @CsvSource({"true, EMAIL_ADDED_SUCCESS", "false, ERROR"})
    void shouldSaveEmail(boolean saveSuccessful, MessageKey expectedMessage) {
        String email = "my.mail@example.org";
        PlayerAuth auth = givenAuthenticatedPlayerWithoutEmail("testEr");
        given(dataSource.updateEmail(any(PlayerAuth.class))).willReturn(saveSuccessful);
        given(validationService.validateEmail(email)).willReturn(true);
        given(validationService.isEmailFreeForRegistration(email, player)).willReturn(true);
        given(emailService.hasAllInformation()).willReturn(false);
        givenEmailChangedEvent(email);

        asyncAddEmail.addEmail(player, email);

        verify(dataSource).updateEmail(auth);
        verify(service).send(player, expectedMessage);
        if (saveSuccessful) {
            verify(auth).setEmail(email);
            verify(playerCache).updatePlayerIfPresent(auth);
        }
    }

    @Test
    void shouldSendConfirmationCodeWhenEmailIsConfigured() {
        String email = "my.mail@example.org";
        givenAuthenticatedPlayerWithoutEmail("testEr");
        given(validationService.validateEmail(email)).willReturn(true);
        given(validationService.isEmailFreeForRegistration(email, player)).willReturn(true);
        given(emailService.hasAllInformation()).willReturn(true);
        given(emailService.sendEmailConfirmationMail(any(), any(), any())).willReturn(true);
        givenEmailChangedEvent(email);

        asyncAddEmail.addEmail(player, email);

        verify(dataSource, never()).updateEmail(any());
        verify(pendingEmailVerificationCache).addPending(eq("testEr"), eq(email), anyString());
        verify(service).send(player, MessageKey.EMAIL_CONFIRM_CODE_SENT, email);
    }

    @Test
    void shouldNotAddMailIfPlayerAlreadyHasEmail() {
        given(player.getName()).willReturn("my_Player");
        given(playerCache.isAuthenticated("my_player")).willReturn(true);
        PlayerAuth auth = mock(PlayerAuth.class);
        given(auth.getEmail()).willReturn("another@mail.tld");
        given(playerCache.getAuth("my_player")).willReturn(auth);

        asyncAddEmail.addEmail(player, "some.mail@example.org");

        verify(service).send(player, MessageKey.USAGE_CHANGE_EMAIL);
        verify(playerCache, never()).updatePlayerIfPresent(any(PlayerAuth.class));
    }

    @Test
    void shouldNotAddMailIfItIsInvalid() {
        String email = "invalid_mail";
        givenAuthenticatedPlayerWithoutEmail("my_Player");
        given(validationService.validateEmail(email)).willReturn(false);

        asyncAddEmail.addEmail(player, email);

        verify(service).send(player, MessageKey.INVALID_EMAIL);
        verify(playerCache, never()).updatePlayerIfPresent(any(PlayerAuth.class));
    }

    @Test
    void shouldNotAddMailIfAlreadyUsed() {
        String email = "player@mail.tld";
        givenAuthenticatedPlayerWithoutEmail("TestName");
        given(validationService.validateEmail(email)).willReturn(true);
        given(validationService.isEmailFreeForRegistration(email, player)).willReturn(false);

        asyncAddEmail.addEmail(player, email);

        verify(service).send(player, MessageKey.EMAIL_ALREADY_USED_ERROR);
        verify(playerCache, never()).updatePlayerIfPresent(any(PlayerAuth.class));
    }

    @ParameterizedTest(name = "{0}, registered={1}")
    @CsvSource({"Username12, true, LOGIN_MESSAGE", "user, false, REGISTER_MESSAGE"})
    void shouldShowLoginOrRegisterMessageForUnauthenticatedPlayer(
        String name, boolean isRegistered, MessageKey expectedMessage) {
        given(player.getName()).willReturn(name);
        given(playerCache.isAuthenticated(name.toLowerCase())).willReturn(false);
        given(dataSource.isAuthAvailable(name)).willReturn(isRegistered);

        asyncAddEmail.addEmail(player, "test@mail.com");

        verify(service).send(player, expectedMessage);
        verify(playerCache, never()).updatePlayerIfPresent(any(PlayerAuth.class));
    }

    @Test
    void shouldSendUnloggedMessageWhenCacheEntryVanished() {
        given(player.getName()).willReturn("TestName");
        given(playerCache.isAuthenticated("testname")).willReturn(true);
        given(playerCache.getAuth("testname")).willReturn(null);
        given(dataSource.isAuthAvailable("TestName")).willReturn(true);

        asyncAddEmail.addEmail(player, "player@mail.tld");

        verify(service).send(player, MessageKey.LOGIN_MESSAGE);
        verify(playerCache, never()).updatePlayerIfPresent(any(PlayerAuth.class));
    }

    @Test
    void shouldNotAddOnCancelledEvent() {
        String email = "player@mail.tld";
        givenAuthenticatedPlayerWithoutEmail("TestName");
        given(validationService.validateEmail(email)).willReturn(true);
        given(validationService.isEmailFreeForRegistration(email, player)).willReturn(true);
        EmailChangedEvent event = givenEmailChangedEvent(email);
        event.setCancelled(true);

        asyncAddEmail.addEmail(player, email);

        verify(service).send(player, MessageKey.EMAIL_ADD_NOT_ALLOWED);
        verify(playerCache, never()).updatePlayerIfPresent(any(PlayerAuth.class));
    }

    @ParameterizedTest(name = "registration setting={0}")
    @ValueSource(strings = {"EMAIL_OPTIONAL", "EMAIL_MANDATORY"})
    void shouldAddEmailAccordingToRegistrationSetting(String settingName) {
        RegisterSecondaryArgument argument = RegisterSecondaryArgument.valueOf(settingName);
        String email = "my.mail@example.org";
        given(player.getName()).willReturn("testEr");
        given(playerCache.isAuthenticated("tester")).willReturn(true);
        PlayerAuth auth = mock(PlayerAuth.class);
        given(auth.getEmail()).willReturn(null);
        given(playerCache.getAuth("tester")).willReturn(auth);
        given(validationService.validateEmail(email)).willReturn(true);
        given(validationService.isEmailFreeForRegistration(email, player)).willReturn(true);
        given(emailService.hasAllInformation()).willReturn(true);
        given(service.getProperty(RegistrationSettings.REGISTER_SECOND_ARGUMENT)).willReturn(argument);
        if (argument == RegisterSecondaryArgument.EMAIL_OPTIONAL) {
            given(dataSource.updateEmail(auth)).willReturn(true);
        } else {
            given(emailService.sendEmailConfirmationMail(any(), any(), any())).willReturn(true);
        }
        EmailChangedEvent event = spy(new EmailChangedEvent(player, null, email, false));
        given(bukkitService.createAndCallEvent(any(Function.class))).willReturn(event);

        asyncAddEmail.addEmail(player, email);

        if (argument == RegisterSecondaryArgument.EMAIL_OPTIONAL) {
            verify(auth).setEmail(email);
            verify(dataSource).updateEmail(auth);
            verify(playerCache).updatePlayerIfPresent(auth);
            verify(service).send(player, MessageKey.EMAIL_ADDED_SUCCESS);
            verify(emailService, never()).sendEmailConfirmationMail(any(), any(), any());
            verify(pendingEmailVerificationCache, never()).addPending(any(), any(), any());
        } else {
            verify(dataSource, never()).updateEmail(any());
            verify(pendingEmailVerificationCache).addPending(any(), any(), any());
            verify(service).send(player, MessageKey.EMAIL_CONFIRM_CODE_SENT, email);
        }
    }

}
