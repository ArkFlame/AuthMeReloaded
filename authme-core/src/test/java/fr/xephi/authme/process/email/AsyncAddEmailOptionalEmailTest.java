package fr.xephi.authme.process.email;

import fr.xephi.authme.TestHelper;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.data.auth.PlayerCache;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.events.EmailChangedEvent;
import fr.xephi.authme.mail.EmailService;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.process.register.RegisterSecondaryArgument;
import fr.xephi.authme.service.BukkitService;
import fr.xephi.authme.service.CommonService;
import fr.xephi.authme.service.PendingEmailVerificationCache;
import fr.xephi.authme.service.ValidationService;
import fr.xephi.authme.settings.properties.RegistrationSettings;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

/**
 * Test for {@link AsyncAddEmail} with the {@code EMAIL_OPTIONAL} registration setting.
 */
@ExtendWith(MockitoExtension.class)
class AsyncAddEmailOptionalEmailTest {

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
    static void setUpLogger() {
        TestHelper.setupLogger();
    }

    @Test
    void shouldSaveEmailDirectlyWhenOptionalAndMailIsConfigured() {
        // given
        String email = "my.mail@example.org";
        given(player.getName()).willReturn("testEr");
        given(playerCache.isAuthenticated("tester")).willReturn(true);
        PlayerAuth auth = mock(PlayerAuth.class);
        given(auth.getEmail()).willReturn(null);
        given(playerCache.getAuth("tester")).willReturn(auth);
        given(validationService.validateEmail(email)).willReturn(true);
        given(validationService.isEmailFreeForRegistration(email, player)).willReturn(true);
        given(emailService.hasAllInformation()).willReturn(true);
        given(service.getProperty(RegistrationSettings.REGISTER_SECOND_ARGUMENT))
            .willReturn(RegisterSecondaryArgument.EMAIL_OPTIONAL);
        given(dataSource.updateEmail(auth)).willReturn(true);
        EmailChangedEvent event = spy(new EmailChangedEvent(player, null, email, false));
        given(bukkitService.createAndCallEvent(any(Function.class))).willReturn(event);

        // when
        asyncAddEmail.addEmail(player, email);

        // then
        verify(auth).setEmail(email);
        verify(dataSource).updateEmail(auth);
        verify(playerCache).updatePlayerIfPresent(auth);
        verify(service).send(player, MessageKey.EMAIL_ADDED_SUCCESS);
        verify(emailService, never()).sendEmailConfirmationMail(any(), any(), any());
        verify(pendingEmailVerificationCache, never()).addPending(any(), any(), any());
    }

    @Test
    void shouldStillSendConfirmationCodeWhenEmailIsMandatory() {
        // given
        String email = "my.mail@example.org";
        given(player.getName()).willReturn("testEr");
        given(playerCache.isAuthenticated("tester")).willReturn(true);
        PlayerAuth auth = mock(PlayerAuth.class);
        given(auth.getEmail()).willReturn(null);
        given(playerCache.getAuth("tester")).willReturn(auth);
        given(validationService.validateEmail(email)).willReturn(true);
        given(validationService.isEmailFreeForRegistration(email, player)).willReturn(true);
        given(emailService.hasAllInformation()).willReturn(true);
        given(service.getProperty(RegistrationSettings.REGISTER_SECOND_ARGUMENT))
            .willReturn(RegisterSecondaryArgument.EMAIL_MANDATORY);
        given(emailService.sendEmailConfirmationMail(any(), any(), any())).willReturn(true);
        EmailChangedEvent event = spy(new EmailChangedEvent(player, null, email, false));
        given(bukkitService.createAndCallEvent(any(Function.class))).willReturn(event);

        // when
        asyncAddEmail.addEmail(player, email);

        // then
        verify(dataSource, never()).updateEmail(any());
        verify(pendingEmailVerificationCache).addPending(any(), any(), any());
        verify(service).send(player, MessageKey.EMAIL_CONFIRM_CODE_SENT, email);
    }
}
