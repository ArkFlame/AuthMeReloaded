package fr.xephi.authme.command.executable.register;

import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import fr.xephi.authme.TestHelper;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.data.captcha.RegistrationCaptchaManager;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.mail.EmailService;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.message.Messages;
import fr.xephi.authme.process.Management;
import fr.xephi.authme.process.register.RegisterSecondaryArgument;
import fr.xephi.authme.process.register.RegistrationType;
import fr.xephi.authme.process.register.executors.EmailRegisterParams;
import fr.xephi.authme.process.register.executors.PasswordRegisterParams;
import fr.xephi.authme.process.register.executors.RegistrationMethod;
import fr.xephi.authme.process.register.executors.TwoFactorRegisterParams;
import fr.xephi.authme.security.HashAlgorithm;
import fr.xephi.authme.service.BukkitService;
import fr.xephi.authme.service.CommonService;
import fr.xephi.authme.service.ValidationService;
import fr.xephi.authme.settings.properties.RegistrationSettings;
import fr.xephi.authme.settings.properties.SecuritySettings;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static fr.xephi.authme.IsEqualByReflectionMatcher.hasEqualValuesOnAllFields;
import static fr.xephi.authme.listener.TestPlayerUtils.mockPlayerWithName;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.hamcrest.MockitoHamcrest.argThat;

/**
 * Test for {@link RegisterCommand}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.WARN)
public class RegisterCommandTest {

    @InjectMocks
    private RegisterCommand command;

    @Mock
    private CommonService commonService;

    @Mock
    private Management management;

    @Mock
    private Messages messages;

    @Mock
    private EmailService emailService;

    @Mock
    private ValidationService validationService;

    @Mock
    private RegistrationCaptchaManager registrationCaptchaManager;

    @Mock
    private BukkitService bukkitService;

    @Mock
    private DataSource dataSource;

    @BeforeAll
    public static void setup() {
        TestHelper.setupLogger();
    }

    @BeforeEach
    public void linkMocksAndProvideSettingDefaults() {
        lenient().when(commonService.getProperty(SecuritySettings.PASSWORD_HASH)).thenReturn(HashAlgorithm.BCRYPT);
        lenient().when(commonService.getProperty(RegistrationSettings.REGISTRATION_TYPE)).thenReturn(RegistrationType.PASSWORD);
        lenient().when(commonService.getProperty(RegistrationSettings.REGISTER_SECOND_ARGUMENT))
            .thenReturn(RegisterSecondaryArgument.NONE);
        lenient().when(commonService.getProperty(RegistrationSettings.UNREGISTER_ON_EMAIL_VERIFICATION_FAILURE))
            .thenReturn(false);
        lenient().when(commonService.getProperty(RegistrationSettings.UNREGISTER_AFTER_MINUTES)).thenReturn(10L);
    }

    @Test
    public void shouldNotRunForNonPlayerSender() {
        // given
        CommandSender sender = mock(BlockCommandSender.class);

        // when
        command.executeCommand(sender, Collections.emptyList());

        // then
        verify(messages).send(sender, MessageKey.PLAYER_COMMAND_ONLY_WITH_ALTERNATIVE, "/authme register <playername> <password>");
        verifyNoInteractions(management, emailService);
    }

    @Test
    public void shouldForwardToManagementForTwoFactor() {
        // given
        given(commonService.getProperty(SecuritySettings.PASSWORD_HASH)).willReturn(HashAlgorithm.TWO_FACTOR);
        Player player = mockPlayerWithName("test2");

        // when
        command.executeCommand(player, Collections.emptyList());

        // then
        verify(registrationCaptchaManager).isCaptchaRequired("test2");
        verify(management).performRegister(eq(RegistrationMethod.TWO_FACTOR_REGISTRATION),
            argThat(hasEqualValuesOnAllFields(TwoFactorRegisterParams.of(player))));
        verifyNoInteractions(emailService);
    }

    @Test
    public void shouldReturnErrorForEmptyArguments() {
        // given
        Player player = mock(Player.class);

        // when
        command.executeCommand(player, Collections.emptyList());

        // then
        verify(commonService).send(player, MessageKey.USAGE_REGISTER);
        verifyNoInteractions(management, emailService);
    }

    @Test
    public void shouldReturnErrorForMissingEmailConfirmation() {
        // given
        given(emailService.hasAllInformation()).willReturn(true);
        given(commonService.getProperty(RegistrationSettings.REGISTRATION_TYPE)).willReturn(RegistrationType.EMAIL);
        given(commonService.getProperty(RegistrationSettings.REGISTER_SECOND_ARGUMENT)).willReturn(RegisterSecondaryArgument.EMAIL_MANDATORY);
        given(validationService.validateEmail(anyString())).willReturn(true);
        Player player = mock(Player.class);

        // when
        command.executeCommand(player, Collections.singletonList("test@example.org"));

        // then
        verify(commonService).send(player, MessageKey.USAGE_REGISTER);
        verifyNoInteractions(management);
    }

    @Test
    public void shouldThrowErrorForMissingEmailConfiguration() {
        // given
        given(commonService.getProperty(RegistrationSettings.REGISTRATION_TYPE)).willReturn(RegistrationType.EMAIL);
        given(emailService.hasAllInformation()).willReturn(false);
        Player player = mock(Player.class);

        // when
        command.executeCommand(player, Collections.singletonList("myMail@example.tld"));

        // then
        verify(commonService).send(player, MessageKey.INCOMPLETE_EMAIL_SETTINGS);
        verify(emailService).hasAllInformation();
        verifyNoInteractions(management);
    }

    static Stream<Arguments> invalidEmailCases() {
        return Stream.of(
            Arguments.of("player@example.org", RegisterSecondaryArgument.NONE, false,
                Arrays.asList("player@example.org", "player@example.org"), MessageKey.INVALID_EMAIL),
            Arguments.of("bobber@bobby.org", RegisterSecondaryArgument.CONFIRMATION, true,
                Arrays.asList("bobber@bobby.org", "invalid"), MessageKey.USAGE_REGISTER));
    }

    @ParameterizedTest
    @MethodSource("invalidEmailCases")
    public void shouldRejectInvalidEmail(String playerMail, RegisterSecondaryArgument secondArg,
                                         boolean validEmail, List<String> arguments, MessageKey expectedMessage) {
        // given
        givenEmailRegistration(playerMail, secondArg, validEmail);
        Player player = mock(Player.class);

        // when
        command.executeCommand(player, arguments);

        // then
        verify(validationService).validateEmail(playerMail);
        verify(emailService).hasAllInformation();
        verify(commonService).send(player, expectedMessage);
        verifyNoInteractions(management);
    }

    @Test
    public void shouldPerformEmailRegistration() {
        // given
        String playerMail = "asfd@lakjgre.lds";
        givenEmailRegistration(playerMail, RegisterSecondaryArgument.CONFIRMATION, true);
        Player player = mockPlayerWithName("brett");

        // when
        command.executeCommand(player, Arrays.asList(playerMail, playerMail));

        // then
        verify(registrationCaptchaManager).isCaptchaRequired("brett");
        verify(validationService).validateEmail(playerMail);
        verify(emailService).hasAllInformation();
        verify(management).performRegister(eq(RegistrationMethod.EMAIL_REGISTRATION),
            argThat(hasEqualValuesOnAllFields(EmailRegisterParams.of(player, playerMail))));
    }

    static Stream<Arguments> emailVerificationUnregisterCases() {
        return Stream.of(
            Arguments.of("late@example.org", "lateuser", 1L, null, 1200L, 1),
            Arguments.of("verified@example.org", "verifieduser", 10L, 123L, 12000L, 0));
    }

    @ParameterizedTest
    @MethodSource("emailVerificationUnregisterCases")
    public void shouldScheduleUnregisterForUnverifiedEmailAccount(String email, String name, long minutes,
                                                                  Long lastLogin, long delay, int invocations) {
        // given
        Player player = givenEmailVerificationRegistration(email, name, minutes, lastLogin);

        // when
        command.executeCommand(player, Collections.singletonList(email));

        // then
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(bukkitService).runTaskLater(eq(player), task.capture(), eq(delay));
        task.getValue().run();
        verify(management, times(invocations)).performUnregisterByAdmin(null, name, player);
    }

    private void givenEmailRegistration(String email, RegisterSecondaryArgument secondArg, boolean validEmail) {
        given(validationService.validateEmail(email)).willReturn(validEmail);
        given(commonService.getProperty(RegistrationSettings.REGISTRATION_TYPE)).willReturn(RegistrationType.EMAIL);
        given(commonService.getProperty(RegistrationSettings.REGISTER_SECOND_ARGUMENT)).willReturn(secondArg);
        given(emailService.hasAllInformation()).willReturn(true);
    }

    private void givenPasswordRegistration(RegisterSecondaryArgument secondArg) {
        given(commonService.getProperty(RegistrationSettings.REGISTRATION_TYPE)).willReturn(RegistrationType.PASSWORD);
        given(commonService.getProperty(RegistrationSettings.REGISTER_SECOND_ARGUMENT)).willReturn(secondArg);
    }

    private Player givenEmailVerificationRegistration(String email, String name, long minutes, Long lastLogin) {
        givenEmailRegistration(email, RegisterSecondaryArgument.NONE, true);
        given(commonService.getProperty(RegistrationSettings.UNREGISTER_ON_EMAIL_VERIFICATION_FAILURE))
            .willReturn(true);
        given(commonService.getProperty(RegistrationSettings.UNREGISTER_AFTER_MINUTES)).willReturn(minutes);
        Player player = mockPlayerWithName(name);
        PlayerAuth auth = mock(PlayerAuth.class);
        given(dataSource.getAuth(name)).willReturn(auth);
        given(auth.getLastLogin()).willReturn(lastLogin);
        return player;
    }

    @Test
    public void shouldNotScheduleUnregisterByDefault() {
        // given
        String email = "default@example.org";
        givenEmailRegistration(email, RegisterSecondaryArgument.NONE, true);

        // when
        command.executeCommand(mockPlayerWithName("defaultuser"), Collections.singletonList(email));

        // then
        verifyNoInteractions(bukkitService, dataSource);
    }

    static Stream<Arguments> invalidPasswordConfirmationCases() {
        return Stream.of(
            Arguments.of(Collections.singletonList("arrrr"), MessageKey.USAGE_REGISTER),
            Arguments.of(Arrays.asList("myPass", "mypass"), MessageKey.PASSWORD_MATCH_ERROR));
    }

    @ParameterizedTest
    @MethodSource("invalidPasswordConfirmationCases")
    public void shouldRejectInvalidPasswordConfirmation(List<String> arguments, MessageKey expectedMessage) {
        // given
        givenPasswordRegistration(RegisterSecondaryArgument.CONFIRMATION);
        Player player = mock(Player.class);

        // when
        command.executeCommand(player, arguments);

        // then
        verify(commonService).send(player, expectedMessage);
        verifyNoInteractions(management, emailService);
    }

    static Stream<Arguments> singleArgumentPasswordRegistrations() {
        return Stream.of(
            Arguments.of(RegisterSecondaryArgument.NONE, "newPlayer"),
            Arguments.of(RegisterSecondaryArgument.EMAIL_OPTIONAL, "Doa"));
    }

    @ParameterizedTest
    @MethodSource("singleArgumentPasswordRegistrations")
    public void shouldPerformPasswordRegistrationWithOneArgument(RegisterSecondaryArgument secondArg, String name) {
        // given
        givenPasswordRegistration(secondArg);
        Player player = mockPlayerWithName(name);

        // when
        command.executeCommand(player, Collections.singletonList("myPass"));

        // then
        verify(registrationCaptchaManager).isCaptchaRequired(name);
        verify(management).performRegister(eq(RegistrationMethod.PASSWORD_REGISTRATION),
            argThat(hasEqualValuesOnAllFields(PasswordRegisterParams.of(player, "myPass", null))));
    }

    @Test
    public void shouldPerformMailValidationForPasswordWithEmail() {
        // given
        givenPasswordRegistration(RegisterSecondaryArgument.EMAIL_MANDATORY);
        String email = "email@example.org";
        given(validationService.validateEmail(email)).willReturn(true);
        Player player = mock(Player.class);

        // when
        command.executeCommand(player, Arrays.asList("myPass", email));

        // then
        verify(validationService).validateEmail(email);
        verify(management).performRegister(eq(RegistrationMethod.PASSWORD_REGISTRATION),
            argThat(hasEqualValuesOnAllFields(PasswordRegisterParams.of(player, "myPass", email))));
    }

    @Test
    public void shouldStopForInvalidEmail() {
        // given
        givenPasswordRegistration(RegisterSecondaryArgument.EMAIL_OPTIONAL);
        String email = "email@example.org";
        given(validationService.validateEmail(email)).willReturn(false);
        Player player = mockPlayerWithName("Waaa");

        // when
        command.executeCommand(player, Arrays.asList("myPass", email));

        // then
        verify(registrationCaptchaManager).isCaptchaRequired("Waaa");
        verify(validationService).validateEmail(email);
        verify(commonService).send(player, MessageKey.INVALID_EMAIL);
        verifyNoInteractions(management);
    }

    @Test
    public void shouldRequestCaptcha() {
        // given
        given(registrationCaptchaManager.isCaptchaRequired(anyString())).willReturn(true);
        String name = "Brian";
        Player player = mockPlayerWithName(name);
        String captcha = "AB923C";
        given(registrationCaptchaManager.getCaptchaCodeOrGenerateNew(name)).willReturn(captcha);

        // when
        command.executeCommand(player, Arrays.asList("myPass", "myPass"));

        // then
        verify(registrationCaptchaManager).isCaptchaRequired(name);
        verify(commonService).send(player, MessageKey.CAPTCHA_FOR_REGISTRATION_REQUIRED, captcha);
        verifyNoInteractions(management, validationService);
    }

}
