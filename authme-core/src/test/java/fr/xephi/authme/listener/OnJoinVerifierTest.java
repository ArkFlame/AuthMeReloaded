package fr.xephi.authme.listener;

import fr.xephi.authme.TestHelper;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.message.Messages;
import fr.xephi.authme.permission.PermissionsManager;
import fr.xephi.authme.permission.PlayerStatePermission;
import fr.xephi.authme.service.AntiBotService;
import fr.xephi.authme.service.BukkitService;
import fr.xephi.authme.service.FloodgateService;
import fr.xephi.authme.service.ValidationService;
import fr.xephi.authme.settings.Settings;
import fr.xephi.authme.settings.properties.HooksSettings;
import fr.xephi.authme.settings.properties.ProtectionSettings;
import fr.xephi.authme.settings.properties.RegistrationSettings;
import fr.xephi.authme.settings.properties.RestrictionSettings;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerLoginEvent;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static fr.xephi.authme.listener.TestPlayerUtils.createInetAddress;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Test for {@link OnJoinVerifier}.
 */
@ExtendWith(MockitoExtension.class)
class OnJoinVerifierTest {

    private static final UUID BEDROCK_PLAYER_ID = UUID.fromString("753493c9-33ba-4a4a-bf61-1bce9d3c9a71");

    @InjectMocks
    private OnJoinVerifier onJoinVerifier;

    @Mock
    private Settings settings;
    @Mock
    private DataSource dataSource;
    @Mock
    private Messages messages;
    @Mock
    private PermissionsManager permissionsManager;
    @Mock
    private AntiBotService antiBotService;
    @Mock
    private ValidationService validationService;
    @Mock
    private BukkitService bukkitService;
    @Mock
    private FloodgateService floodgateService;
    @Mock
    private Server server;

    @BeforeAll
    static void setUpLogger() {
        TestHelper.setupLogger();
    }

    @Test
    void shouldNotDoAnythingForNormalEvent() {
        PlayerLoginEvent event = mock(PlayerLoginEvent.class);
        given(event.getResult()).willReturn(PlayerLoginEvent.Result.ALLOWED);

        boolean result = onJoinVerifier.refusePlayerForFullServer(event);

        assertThat(result, equalTo(false));
        verify(event).getResult();
        verifyNoMoreInteractions(event);
        verifyNoInteractions(bukkitService, dataSource, permissionsManager);
    }

    @Test
    void shouldRefuseNonVipPlayerForFullServer() {
        Player player = mock(Player.class);
        PlayerLoginEvent event = new PlayerLoginEvent(player, "hostname", null);
        event.setResult(PlayerLoginEvent.Result.KICK_FULL);
        given(player.getName()).willReturn("VipCheck");
        given(permissionsManager.hasPermissionOffline("VipCheck", PlayerStatePermission.IS_VIP)).willReturn(false);
        String serverFullMessage = "server is full";
        given(messages.retrieveSingle("VipCheck", MessageKey.KICK_FULL_SERVER)).willReturn(serverFullMessage);

        boolean result = onJoinVerifier.refusePlayerForFullServer(event);

        assertThat(result, equalTo(true));
        assertThat(event.getResult(), equalTo(PlayerLoginEvent.Result.KICK_FULL));
        assertThat(event.getKickMessage(), equalTo(serverFullMessage));
        verifyNoInteractions(bukkitService, dataSource);
    }

    @Test
    void shouldKickNonVipForJoiningVipPlayer() {
        Player player = mock(Player.class);
        PlayerLoginEvent event = new PlayerLoginEvent(player, "hostname", null);
        event.setResult(PlayerLoginEvent.Result.KICK_FULL);
        given(player.getName()).willReturn("VipPlayer");
        given(permissionsManager.hasPermissionOffline("VipPlayer", PlayerStatePermission.IS_VIP)).willReturn(true);
        List<Player> onlinePlayers = Arrays.asList(mock(Player.class), mock(Player.class));
        given(permissionsManager.hasPermission(onlinePlayers.get(0), PlayerStatePermission.IS_VIP)).willReturn(true);
        given(permissionsManager.hasPermission(onlinePlayers.get(1), PlayerStatePermission.IS_VIP)).willReturn(false);
        given(bukkitService.getOnlinePlayers()).willReturn(onlinePlayers);
        given(server.getMaxPlayers()).willReturn(onlinePlayers.size());
        given(messages.retrieveSingle("VipPlayer", MessageKey.KICK_FOR_VIP)).willReturn("kick for vip");

        boolean result = onJoinVerifier.refusePlayerForFullServer(event);

        assertThat(result, equalTo(false));
        assertThat(event.getResult(), equalTo(PlayerLoginEvent.Result.ALLOWED));
        // First player is VIP, so expect no interactions there and second player to have been kicked
        verifyNoInteractions(onlinePlayers.get(0));
        verify(onlinePlayers.get(1)).kickPlayer("kick for vip");
    }

    @Test
    void shouldKickVipPlayerIfNoPlayerCanBeKicked() {
        Player player = mock(Player.class);
        PlayerLoginEvent event = new PlayerLoginEvent(player, "hostname", null);
        event.setResult(PlayerLoginEvent.Result.KICK_FULL);
        given(player.getName()).willReturn("VipPlayer");
        given(permissionsManager.hasPermissionOffline("VipPlayer", PlayerStatePermission.IS_VIP)).willReturn(true);
        List<Player> onlinePlayers = Collections.singletonList(mock(Player.class));
        given(permissionsManager.hasPermission(onlinePlayers.get(0), PlayerStatePermission.IS_VIP)).willReturn(true);
        given(bukkitService.getOnlinePlayers()).willReturn(onlinePlayers);
        given(server.getMaxPlayers()).willReturn(onlinePlayers.size());
        given(messages.retrieveSingle("VipPlayer", MessageKey.KICK_FULL_SERVER)).willReturn("kick full server");

        boolean result = onJoinVerifier.refusePlayerForFullServer(event);

        assertThat(result, equalTo(true));
        assertThat(event.getResult(), equalTo(PlayerLoginEvent.Result.KICK_FULL));
        assertThat(event.getKickMessage(), equalTo("kick full server"));
        verifyNoInteractions(onlinePlayers.get(0));
    }

    @Test
    void shouldReturnKickMessageForNonVipPlayerOnFullServer() {
        given(permissionsManager.hasPermissionOffline("RegularPlayer", PlayerStatePermission.IS_VIP)).willReturn(false);
        given(messages.retrieveSingle("RegularPlayer", MessageKey.KICK_FULL_SERVER)).willReturn("server is full");

        String kickMessage = onJoinVerifier.getServerFullKickMessageIfDenied("RegularPlayer");

        assertThat(kickMessage, equalTo("server is full"));
        verifyNoInteractions(bukkitService, dataSource);
    }

    @Test
    void shouldKickNonRegistered() {
        given(settings.getProperty(RestrictionSettings.KICK_NON_REGISTERED)).willReturn(true);

        expectValidationExceptionWith(() -> onJoinVerifier.checkKickNonRegistered(false),
            MessageKey.MUST_REGISTER_MESSAGE);
    }

    @Test
    void shouldNotKickRegisteredPlayer() throws FailedVerificationException {
        onJoinVerifier.checkKickNonRegistered(true);
    }

    @Test
    void shouldNotKickUnregisteredPlayer() throws FailedVerificationException {
        given(settings.getProperty(RestrictionSettings.KICK_NON_REGISTERED)).willReturn(false);

        onJoinVerifier.checkKickNonRegistered(false);
    }

    @Test
    void shouldAllowValidName() throws FailedVerificationException {
        configureNicknameCharacterCheck(8);

        onJoinVerifier.checkIsValidName("Bobby5");
    }

    @Test
    void shouldRejectTooLongName() {
        given(settings.getProperty(RestrictionSettings.MAX_NICKNAME_LENGTH)).willReturn(8);
        given(settings.getProperty(RestrictionSettings.ALLOWED_NICKNAME_CHARACTERS)).willReturn("[a-zA-Z0-9]+");
        onJoinVerifier.reload(); // @PostConstruct method

        expectValidationExceptionWith(() -> onJoinVerifier.checkIsValidName("longerthaneight"),
            MessageKey.INVALID_NAME_LENGTH);
    }

    @Test
    void shouldRejectTooShortName() {
        configureNicknameCharacterCheck(8);

        expectValidationExceptionWith(() -> onJoinVerifier.checkIsValidName("abc"),
            MessageKey.INVALID_NAME_LENGTH);
    }

    @Test
    void shouldRejectNameWithInvalidCharacters() {
        configureNicknameCharacterCheck(8);

        expectValidationExceptionWith(() -> onJoinVerifier.checkIsValidName("Tester!"),
            MessageKey.INVALID_NAME_CHARACTERS, "[a-zA-Z0-9]+");
    }

    @Test
    void shouldSkipNameCharacterCheckForVerifiedBedrockPlayer() {
        UUID playerId = BEDROCK_PLAYER_ID;
        configureNicknameCharacterCheck(16);
        given(settings.getProperty(HooksSettings.IGNORE_BEDROCK_NAME_CHECK)).willReturn(true);
        given(floodgateService.isFloodgatePlayer(playerId)).willReturn(true);

        assertDoesNotThrow(() -> onJoinVerifier.checkIsValidName("Tester!", playerId));
        verify(floodgateService).isFloodgatePlayer(playerId);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Tester!", "Bedrock_Tester"})
    void shouldStillCheckNameOfNonBedrockPlayer(String name) {
        UUID playerId = BEDROCK_PLAYER_ID;
        configureNicknameCharacterCheck(16);
        given(settings.getProperty(HooksSettings.IGNORE_BEDROCK_NAME_CHECK)).willReturn(true);
        given(floodgateService.isFloodgatePlayer(playerId)).willReturn(false);

        expectValidationExceptionWith(() -> onJoinVerifier.checkIsValidName(name, playerId),
            MessageKey.INVALID_NAME_CHARACTERS, "[a-zA-Z0-9]+");
    }

    @Test
    void shouldStillCheckNameWhenBedrockBypassIsDisabled() {
        UUID playerId = BEDROCK_PLAYER_ID;
        configureNicknameCharacterCheck(16);
        given(settings.getProperty(HooksSettings.IGNORE_BEDROCK_NAME_CHECK)).willReturn(false);

        expectValidationExceptionWith(() -> onJoinVerifier.checkIsValidName("Tester!", playerId),
            MessageKey.INVALID_NAME_CHARACTERS, "[a-zA-Z0-9]+");
        verifyNoInteractions(floodgateService);
    }

    @Test
    void shouldAllowProperlyCasedName() throws FailedVerificationException {
        String name = "Bobby";
        PlayerAuth auth = PlayerAuth.builder().name("bobby").realName("Bobby").build();
        given(settings.getProperty(RegistrationSettings.PREVENT_OTHER_CASE)).willReturn(true);

        onJoinVerifier.checkNameCasing(name, auth);

        verifyNoInteractions(dataSource);
    }

    @Test
    void shouldRejectNameWithWrongCasing() {
        String name = "Tester";
        PlayerAuth auth = PlayerAuth.builder().name("tester").realName("testeR").build();
        given(settings.getProperty(RegistrationSettings.PREVENT_OTHER_CASE)).willReturn(true);

        expectValidationExceptionWith(() -> onJoinVerifier.checkNameCasing(name, auth),
            MessageKey.INVALID_NAME_CASE, "testeR", "Tester");

        verifyNoInteractions(dataSource);
    }

    @Test
    void shouldUpdateMissingRealName() throws FailedVerificationException {
        String name = "Authme";
        PlayerAuth auth = PlayerAuth.builder().name("authme").realName("").build();
        given(settings.getProperty(RegistrationSettings.PREVENT_OTHER_CASE)).willReturn(true);

        onJoinVerifier.checkNameCasing(name, auth);

        verify(dataSource).updateRealName("authme", "Authme");
    }

    @Test
    void shouldUpdateDefaultRealName() throws FailedVerificationException {
        String name = "SOMEONE";
        PlayerAuth auth = PlayerAuth.builder().name("someone").realName("Player").build();
        given(settings.getProperty(RegistrationSettings.PREVENT_OTHER_CASE)).willReturn(true);

        onJoinVerifier.checkNameCasing(name, auth);

        verify(dataSource).updateRealName("someone", "SOMEONE");
    }

    @Test
    void shouldAcceptCasingMismatchForDisabledSetting() throws FailedVerificationException {
        String name = "Test";
        PlayerAuth auth = PlayerAuth.builder().name("test").realName("TEST").build();
        given(settings.getProperty(RegistrationSettings.PREVENT_OTHER_CASE)).willReturn(false);

        onJoinVerifier.checkNameCasing(name, auth);

        verifyNoInteractions(dataSource);
    }

    @Test
    void shouldAcceptNameForUnregisteredAccount() throws FailedVerificationException {
        String name = "MyPlayer";
        PlayerAuth auth = null;

        onJoinVerifier.checkNameCasing(name, auth);

        verifyNoInteractions(dataSource);
    }

    @Test
    void shouldAcceptNameThatIsNotOnline() throws FailedVerificationException {
        String name = "bobby";
        given(settings.getProperty(RestrictionSettings.FORCE_SINGLE_SESSION)).willReturn(true);
        given(bukkitService.getOnlinePlayers()).willReturn(Collections.emptyList());

        onJoinVerifier.checkSingleSession(name);

        verify(bukkitService).getOnlinePlayers();
    }

    @Test
    void shouldRejectNameAlreadyOnline() {
        String name = "Charlie";

        Player onlinePlayer = mock(Player.class);
        given(onlinePlayer.getName()).willReturn("CHARLIE");
        given(bukkitService.getOnlinePlayers()).willReturn(Collections.singletonList(onlinePlayer));
        given(settings.getProperty(RestrictionSettings.FORCE_SINGLE_SESSION)).willReturn(true);

        expectValidationExceptionWith(() -> onJoinVerifier.checkSingleSession(name),
            MessageKey.USERNAME_ALREADY_ONLINE_ERROR);
    }

    @Test
    void shouldAcceptAlreadyOnlineNameForDisabledSetting() throws FailedVerificationException {
        String name = "Felipe";
        given(settings.getProperty(RestrictionSettings.FORCE_SINGLE_SESSION)).willReturn(false);

        onJoinVerifier.checkSingleSession(name);

        verifyNoInteractions(bukkitService);
    }

    @Test
    void shouldAllowUser() throws FailedVerificationException {
        String name = "Bobby";
        boolean isAuthAvailable = false;
        given(permissionsManager.hasPermissionOffline(name, PlayerStatePermission.BYPASS_ANTIBOT)).willReturn(false);
        given(antiBotService.shouldKick()).willReturn(false);

        onJoinVerifier.checkAntibot(name, isAuthAvailable);

        verify(permissionsManager).hasPermissionOffline(name, PlayerStatePermission.BYPASS_ANTIBOT);
        verify(antiBotService).shouldKick();
    }

    @Test
    void shouldAllowUserWithAuth() throws FailedVerificationException {
        String name = "Lacey";
        boolean isAuthAvailable = true;

        onJoinVerifier.checkAntibot(name, isAuthAvailable);

        verifyNoInteractions(permissionsManager, antiBotService);
    }

    @Test
    void shouldAllowUserWithBypassPermission() throws FailedVerificationException {
        String name = "Steward";
        boolean isAuthAvailable = false;
        given(permissionsManager.hasPermissionOffline(name, PlayerStatePermission.BYPASS_ANTIBOT)).willReturn(true);

        onJoinVerifier.checkAntibot(name, isAuthAvailable);

        verify(permissionsManager).hasPermissionOffline(name, PlayerStatePermission.BYPASS_ANTIBOT);
        verifyNoInteractions(antiBotService);
    }

    @Test
    void shouldKickUserForFailedAntibotCheck() {
        String name = "D3";
        boolean isAuthAvailable = false;
        given(permissionsManager.hasPermissionOffline(name, PlayerStatePermission.BYPASS_ANTIBOT)).willReturn(false);
        given(antiBotService.shouldKick()).willReturn(true);

        try {
            onJoinVerifier.checkAntibot(name, isAuthAvailable);
            fail("Expected exception to be thrown");
        } catch (FailedVerificationException e) {
            verify(permissionsManager).hasPermissionOffline(name, PlayerStatePermission.BYPASS_ANTIBOT);
            verify(antiBotService).shouldKick();
        }

    }

    /**
     * Tests various scenarios in which the country check should not take place.
     */
    @Test
    void shouldNotCheckCountry() throws FailedVerificationException {
        String name = "david";
        String ip = "127.0.0.1";

        given(settings.getProperty(ProtectionSettings.ENABLE_PROTECTION)).willReturn(false);
        given(settings.getProperty(ProtectionSettings.ENABLE_PROTECTION_REGISTERED)).willReturn(true);

        onJoinVerifier.checkPlayerCountry(name, ip, false);
        onJoinVerifier.checkPlayerCountry(name, ip, true);

        verifyNoInteractions(validationService);
    }

    @Test
    void shouldCheckAndAcceptUnregisteredPlayerCountry() throws FailedVerificationException {
        String ip = "192.168.0.1";
        String name = "lucas";
        given(settings.getProperty(ProtectionSettings.ENABLE_PROTECTION)).willReturn(true);
        given(validationService.isCountryAdmitted(ip)).willReturn(true);

        onJoinVerifier.checkPlayerCountry(name, ip, false);

        verify(validationService).isCountryAdmitted(ip);
    }

    @Test
    void shouldCheckAndAcceptRegisteredPlayerCountry() throws FailedVerificationException {
        String ip = "192.168.10.24";
        String name = "gabriel";
        given(settings.getProperty(ProtectionSettings.ENABLE_PROTECTION)).willReturn(true);
        given(settings.getProperty(ProtectionSettings.ENABLE_PROTECTION_REGISTERED)).willReturn(true);
        given(validationService.isCountryAdmitted(ip)).willReturn(true);

        onJoinVerifier.checkPlayerCountry(name, ip, true);

        verify(validationService).isCountryAdmitted(ip);
    }

    @Test
    void shouldThrowForBannedCountry() {
        String ip = "192.168.40.0";
        String name = "bob";
        given(settings.getProperty(ProtectionSettings.ENABLE_PROTECTION)).willReturn(true);
        given(validationService.isCountryAdmitted(ip)).willReturn(false);

        expectValidationExceptionWith(() -> onJoinVerifier.checkPlayerCountry(name, ip, false),
            MessageKey.COUNTRY_BANNED_ERROR);
    }

    @Test
    void shouldAllowPlayerWithMatchingNameRestriction() throws FailedVerificationException {
        String name = "Bobby";
        InetAddress address = createInetAddress("127.0.0.4");
        given(validationService.fulfillsNameRestrictions(name, address)).willReturn(true);

        onJoinVerifier.checkNameRestrictions(name, address);

        verify(validationService).fulfillsNameRestrictions(name, address);
        verifyNoInteractions(settings, server);
    }

    @Test
    void shouldKickPlayerWithUnmatchedNameRestriction() {
        String name = "Bobby";
        InetAddress address = createInetAddress("99.99.99.99");
        given(validationService.fulfillsNameRestrictions(name, address)).willReturn(false);
        given(settings.getProperty(RestrictionSettings.BAN_UNKNOWN_IP)).willReturn(false);

        expectValidationExceptionWith(() -> onJoinVerifier.checkNameRestrictions(name, address),
            MessageKey.NOT_OWNER_ERROR);
    }

    @Test
    void shouldBanIpWhenNameRestrictionFailsAndBanIsEnabled() {
        String name = "Bobby";
        InetAddress address = createInetAddress("99.99.99.99");
        given(validationService.fulfillsNameRestrictions(name, address)).willReturn(false);
        given(settings.getProperty(RestrictionSettings.BAN_UNKNOWN_IP)).willReturn(true);

        try {
            onJoinVerifier.checkNameRestrictions(name, address);
            fail("Expected FailedVerificationException to be thrown");
        } catch (FailedVerificationException e) {
            assertThat(e.getReason(), equalTo(MessageKey.NOT_OWNER_ERROR));
        }

        verify(bukkitService).runOnGlobalRegion(any(Runnable.class));
    }

    private void configureNicknameCharacterCheck(int maxLength) {
        given(settings.getProperty(RestrictionSettings.MIN_NICKNAME_LENGTH)).willReturn(4);
        given(settings.getProperty(RestrictionSettings.MAX_NICKNAME_LENGTH)).willReturn(maxLength);
        given(settings.getProperty(RestrictionSettings.ALLOWED_NICKNAME_CHARACTERS)).willReturn("[a-zA-Z0-9]+");
        onJoinVerifier.reload();
    }

    private void expectValidationExceptionWith(Executable executable, MessageKey messageKey, String... args) {
        FailedVerificationException exception = assertThrows(FailedVerificationException.class, executable);
        assertThat(exception, exceptionWithData(messageKey, args));
    }

    private static Matcher<FailedVerificationException> exceptionWithData(final MessageKey messageKey,
                                                                          final String... args) {
        return new TypeSafeMatcher<FailedVerificationException>() {
            @Override
            protected boolean matchesSafely(FailedVerificationException item) {
                return messageKey.equals(item.getReason()) && Arrays.equals(args, item.getArgs());
            }

            @Override
            public void describeTo(Description description) {
                description.appendValue("VerificationFailedException: reason=" + messageKey + ";args="
                    + (args == null ? "null" : String.join(", ", args)));
            }
        };
    }

}
