package fr.xephi.authme.service;

import com.google.common.base.Strings;
import fr.xephi.authme.TestHelper;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.permission.PermissionsManager;
import fr.xephi.authme.permission.PlayerStatePermission;
import fr.xephi.authme.service.ValidationService.ValidationResult;
import fr.xephi.authme.settings.Settings;
import fr.xephi.authme.settings.properties.EmailSettings;
import fr.xephi.authme.settings.properties.ProtectionSettings;
import fr.xephi.authme.settings.properties.RestrictionSettings;
import fr.xephi.authme.settings.properties.SecuritySettings;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static com.google.common.collect.Sets.newHashSet;
import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Test for {@link ValidationService}.
 */
@ExtendWith(MockitoExtension.class)
class ValidationServiceTest {

    @InjectMocks
    private ValidationService validationService;
    @Mock
    private Settings settings;
    @Mock
    private DataSource dataSource;
    @Mock
    private PermissionsManager permissionsManager;
    @Mock
    private GeoIpService geoIpService;

    @BeforeEach
    void callReload() {
        given(settings.getProperty(RestrictionSettings.ALLOWED_PASSWORD_REGEX)).willReturn("[a-zA-Z]+");
        given(settings.getProperty(RestrictionSettings.ALLOWED_EMAIL_REGEX)).willReturn(".*");
        given(settings.getProperty(RestrictionSettings.ENABLE_RESTRICTED_USERS)).willReturn(false);
        validationService.reload();
    }

    @Test
    void shouldRejectPasswordSameAsUsername() {
        // given / when
        ValidationResult error = validationService.validatePassword("bobby", "Bobby");

        // then
        assertErrorEquals(error, MessageKey.PASSWORD_IS_USERNAME_ERROR);
    }

    @Test
    void shouldRejectPasswordNotMatchingPattern() {
        // given / when
        // service mock returns pattern a-zA-Z -> numbers should not be accepted
        ValidationResult error = validationService.validatePassword("invalid1234", "myPlayer");

        // then
        assertErrorEquals(error, MessageKey.PASSWORD_CHARACTERS_ERROR, "[a-zA-Z]+");
    }

    @Test
    void shouldRejectTooShortPassword() {
        // given
        given(settings.getProperty(SecuritySettings.MIN_PASSWORD_LENGTH)).willReturn(3);

        // when
        ValidationResult error = validationService.validatePassword("ab", "tester");

        // then
        assertErrorEquals(error, MessageKey.INVALID_PASSWORD_LENGTH);
    }

    @Test
    void shouldRejectTooLongPassword() {
        // given
        given(settings.getProperty(SecuritySettings.MIN_PASSWORD_LENGTH)).willReturn(3);
        given(settings.getProperty(SecuritySettings.MAX_PASSWORD_LENGTH)).willReturn(20);

        // when
        ValidationResult error = validationService.validatePassword(Strings.repeat("a", 30), "player");

        // then
        assertErrorEquals(error, MessageKey.INVALID_PASSWORD_LENGTH);
    }

    @ParameterizedTest(name = "{0} / {1}: {2}")
    @MethodSource("passwordSafetyCases")
    void shouldCheckPasswordSafety(String password, String username, MessageKey expectedError) {
        // given
        givenPasswordSettings();

        // when
        ValidationResult result = validationService.validatePassword(password, username);

        // then
        if (expectedError == null) {
            assertThat(result.hasError(), equalTo(false));
        } else {
            assertErrorEquals(result, expectedError);
        }
    }

    private static Stream<Arguments> passwordSafetyCases() {
        return Stream.of(
            Arguments.of("unsafe", "playertest", MessageKey.PASSWORD_UNSAFE_ERROR),
            Arguments.of("safePass", "some_user", null));
    }

    @Test
    void shouldAcceptEmailWithEmptyLists() {
        // given
        given(settings.getProperty(EmailSettings.DOMAIN_WHITELIST)).willReturn(Collections.emptyList());
        given(settings.getProperty(EmailSettings.DOMAIN_BLACKLIST)).willReturn(Collections.emptyList());

        // when
        boolean result = validationService.validateEmail("test@example.org");

        // then
        assertThat(result, equalTo(true));
    }

    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("domainFilterEmails")
    void shouldCheckEmailAgainstDomainFilters(String email, boolean expected, List<String> whitelist,
                                              List<String> blacklist) {
        // given
        given(settings.getProperty(EmailSettings.DOMAIN_WHITELIST)).willReturn(whitelist);
        if (whitelist.isEmpty()) {
            given(settings.getProperty(EmailSettings.DOMAIN_BLACKLIST)).willReturn(blacklist);
        }

        // when
        boolean result = validationService.validateEmail(email);

        // then
        assertThat(result, equalTo(expected));
    }

    private static Stream<Arguments> domainFilterEmails() {
        return Stream.of(
            Arguments.of("TesT@Example.com", true, asList("domain.tld", "example.com"), Collections.emptyList()),
            Arguments.of("email@other-domain.abc", false, asList("domain.tld", "example.com"), Collections.emptyList()),
            Arguments.of("sample@valid-name.tld", true, Collections.emptyList(), asList("Example.org", "a-test-name.tld")),
            Arguments.of("sample@a-Test-name.tld", false, Collections.emptyList(), asList("Example.org", "a-test-name.tld")));
    }

    @Test
    void shouldRejectEmailNotMatchingConfiguredRegex() {
        // given
        given(settings.getProperty(RestrictionSettings.ALLOWED_EMAIL_REGEX))
            .willReturn("^[^@]+@example\\.org$");
        validationService.reload();

        // when / then
        assertThat(validationService.validateEmail("user@example.com"), equalTo(false));
        assertThat(validationService.validateEmail("user@example.org"), equalTo(true));
    }

    @Test
    void shouldApplyDefaultRegexWithoutRestrictingLegacyEmails() {
        // given: the @BeforeEach stub uses the default '.*' regex
        // when / then
        assertThat(validationService.validateEmail("some.user+tag@example.co.uk"), equalTo(true));
    }

    @ParameterizedTest(name = "reject invalid email: {0}")
    @ValueSource(strings = {"invalidinput", "invalidinput@", "your@email.com", "user@example.com@other-domain.abc"})
    void shouldRejectInvalidEmail(String email) {
        // given / when / then
        assertThat(validationService.validateEmail(email), equalTo(false));
    }

    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("emailRegistrationCounts")
    void shouldCheckEmailFreeForRegistration(String email, int count, boolean expected) {
        // given
        CommandSender sender = mock(CommandSender.class);
        given(permissionsManager.hasPermission(sender, PlayerStatePermission.ALLOW_MULTIPLE_ACCOUNTS))
            .willReturn(false);
        given(dataSource.countAuthsByEmail(email)).willReturn(count);
        given(settings.getProperty(EmailSettings.MAX_REG_PER_EMAIL)).willReturn(3);

        // when
        boolean result = validationService.isEmailFreeForRegistration(email, sender);

        // then
        assertThat(result, equalTo(expected));
    }

    private static Stream<Arguments> emailRegistrationCounts() {
        return Stream.of(
            Arguments.of("my.address@example.org", 2, true),
            Arguments.of("mail@example.org", 5, false));
    }

    @Test
    void shouldAllowBypassForPresentPermission() {
        // given
        CommandSender sender = mock(CommandSender.class);
        String email = "mail-address@example.com";
        given(permissionsManager.hasPermission(sender, PlayerStatePermission.ALLOW_MULTIPLE_ACCOUNTS))
            .willReturn(true);

        // when
        boolean result = validationService.isEmailFreeForRegistration(email, sender);

        // then
        verifyNoInteractions(dataSource);
        assertThat(result, equalTo(true));
    }

    @Test
    void shouldRecognizeUnrestrictedNames() {
        // given
        given(settings.getProperty(RestrictionSettings.UNRESTRICTED_NAMES)).willReturn(newHashSet("name01", "npc"));

        // when / then
        assertThat(validationService.isUnrestricted("npc"), equalTo(true));
        assertThat(validationService.isUnrestricted("someplayer"), equalTo(false));
        assertThat(validationService.isUnrestricted("NAME01"), equalTo(true));

        // Check reloading
        given(settings.getProperty(RestrictionSettings.UNRESTRICTED_NAMES)).willReturn(newHashSet("new", "names"));
        validationService.reload();
        assertThat(validationService.isUnrestricted("npc"), equalTo(false));
        assertThat(validationService.isUnrestricted("New"), equalTo(true));
    }

    @Test
    void shouldNotInvokeGeoLiteApiIfCountryListsAreEmpty() {
        // given
        given(settings.getProperty(ProtectionSettings.COUNTRIES_WHITELIST)).willReturn(Collections.emptyList());
        given(settings.getProperty(ProtectionSettings.COUNTRIES_BLACKLIST)).willReturn(Collections.emptyList());

        // when
        boolean result = validationService.isCountryAdmitted("addr");

        // then
        assertThat(result, equalTo(true));
        verifyNoInteractions(geoIpService);
    }

    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("countryCases")
    void shouldCheckCountryAgainstFilters(String ip, String countryCode, boolean expected, List<String> whitelist,
                                          List<String> blacklist) {
        // given
        given(settings.getProperty(ProtectionSettings.COUNTRIES_WHITELIST)).willReturn(whitelist);
        if (whitelist.isEmpty()) {
            given(settings.getProperty(ProtectionSettings.COUNTRIES_BLACKLIST)).willReturn(blacklist);
        }
        given(geoIpService.getCountryCode(ip)).willReturn(countryCode);

        // when
        boolean result = validationService.isCountryAdmitted(ip);

        // then
        assertThat(result, equalTo(expected));
        verify(geoIpService).getCountryCode(ip);
    }

    private static Stream<Arguments> countryCases() {
        return Stream.of(
            Arguments.of("127.0.0.1", "CH", true, asList("ch", "it"), Collections.emptyList()),
            Arguments.of("123.45.67.89", "BR", false, asList("ch", "it"), Collections.emptyList()),
            Arguments.of("127.0.0.1", "BR", true, Collections.emptyList(), asList("ch", "it")),
            Arguments.of("123.45.67.89", "IT", false, Collections.emptyList(), asList("ch", "it")));
    }

    @Test
    void shouldCheckNameRestrictions() {
        // given
        given(settings.getProperty(RestrictionSettings.ENABLE_RESTRICTED_USERS)).willReturn(true);
        given(settings.getProperty(RestrictionSettings.RESTRICTED_USERS))
            .willReturn(newHashSet("Bobby;127.0.0.4", "Tamara;32.24.16.8", "Gabriel;regex:93\\.23\\.44\\..*", "emanuel;94.65.24.*", "imyourisp;*.yourisp.net"));
        validationService.reload();

        Player bobby = TestPlayerFactory.mockPlayerWithNameAndIp("bobby", "127.0.0.4");
        Player tamara = TestPlayerFactory.mockPlayerWithNameAndIp("taMARA", "8.8.8.8");
        Player gabriel = TestPlayerFactory.mockPlayerWithNameAndIp("Gabriel", "93.23.44.65");
        Player gabriel2 = TestPlayerFactory.mockPlayerWithNameAndIp("Gabriel", "93.23.33.34");
        Player emanuel = TestPlayerFactory.mockPlayerWithNameAndIp("emanuel", "94.65.24.10");
        Player emanuel2 = TestPlayerFactory.mockPlayerWithNameAndIp("emanuel", "94.65.60.10");
        Player imYourIsp = TestPlayerFactory.mockPlayerWithNameAndIp("imyourisp", "65.65.65.65");
        Player notRestricted = mock(Player.class);
        given(notRestricted.getName()).willReturn("notRestricted");

        ValidationService validationServiceSpy = Mockito.spy(validationService);
        willReturn("bogus.tld").given(validationServiceSpy).getHostName(any(InetSocketAddress.class));
        InetSocketAddress imYourIspSocketAddr = imYourIsp.getAddress();
        willReturn("bazinga.yourisp.net").given(validationServiceSpy).getHostName(imYourIspSocketAddr);

        // when
        boolean isBobbyAdmitted = validationServiceSpy.fulfillsNameRestrictions(bobby);
        boolean isTamaraAdmitted = validationServiceSpy.fulfillsNameRestrictions(tamara);
        boolean isGabrielAdmitted = validationServiceSpy.fulfillsNameRestrictions(gabriel);
        boolean isGabriel2Admitted = validationServiceSpy.fulfillsNameRestrictions(gabriel2);
        boolean isEmanuelAdmitted = validationServiceSpy.fulfillsNameRestrictions(emanuel);
        boolean isEmanuel2Admitted = validationServiceSpy.fulfillsNameRestrictions(emanuel2);
        boolean isImYourIspAdmitted = validationServiceSpy.fulfillsNameRestrictions(imYourIsp);
        boolean isNotRestrictedAdmitted = validationServiceSpy.fulfillsNameRestrictions(notRestricted);

        // then
        assertThat(isBobbyAdmitted, equalTo(true));
        assertThat(isTamaraAdmitted, equalTo(false));
        assertThat(isGabrielAdmitted, equalTo(true));
        assertThat(isGabriel2Admitted, equalTo(false));
        assertThat(isEmanuelAdmitted, equalTo(true));
        assertThat(isEmanuel2Admitted, equalTo(false));
        assertThat(isImYourIspAdmitted, equalTo(true));
        assertThat(isNotRestrictedAdmitted, equalTo(true));
        verify(notRestricted, only()).getName();
    }

    @Test
    void shouldLogWarningForInvalidRestrictionRule() {
        // given
        Logger logger = TestHelper.setupLogger();
        given(settings.getProperty(RestrictionSettings.ENABLE_RESTRICTED_USERS)).willReturn(true);
        given(settings.getProperty(RestrictionSettings.RESTRICTED_USERS))
            .willReturn(newHashSet("Bobby;127.0.0.4", "Tamara;"));

        // when
        validationService.reload();

        // then
        ArgumentCaptor<String> stringCaptor = ArgumentCaptor.forClass(String.class);
        verify(logger).warning(stringCaptor.capture());
        assertThat(stringCaptor.getValue(), containsString("Tamara;"));
    }

    private void givenPasswordSettings() {
        given(settings.getProperty(SecuritySettings.MIN_PASSWORD_LENGTH)).willReturn(3);
        given(settings.getProperty(SecuritySettings.MAX_PASSWORD_LENGTH)).willReturn(20);
        given(settings.getProperty(SecuritySettings.UNSAFE_PASSWORDS)).willReturn(newHashSet("unsafe", "other-unsafe"));
    }

    private static void assertErrorEquals(ValidationResult validationResult, MessageKey messageKey, String... args) {
        assertThat(validationResult.hasError(), equalTo(true));
        assertThat(validationResult.getMessageKey(), equalTo(messageKey));
        assertThat(validationResult.getArgs(), equalTo(args));
    }
}
