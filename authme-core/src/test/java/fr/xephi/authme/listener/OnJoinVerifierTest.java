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
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static fr.xephi.authme.listener.TestPlayerUtils.createInetAddress;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
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
        PlayerLoginEvent event = createFullServerEvent(player);
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
        PlayerLoginEvent event = createFullServerEvent(player);
        given(player.getName()).willReturn("VipPlayer");
        given(permissionsManager.hasPermissionOffline("VipPlayer", PlayerStatePermission.IS_VIP)).willReturn(true);
        List<Player> onlinePlayers = Arrays.asList(mock(Player.class), mock(Player.class));
        given(permissionsManager.hasPermission(onlinePlayers.get(0), PlayerStatePermission.IS_VIP)).willReturn(true);
        given(permissionsManager.hasPermission(onlinePlayers.get(1), PlayerStatePermission.IS_VIP)).willReturn(false);
        given(bukkitService.getOnlinePlayers()).willReturn(onlinePlayers);
        given(server.getMaxPlayers()).willReturn(onlinePlayers.size());
        given(messages.retrieveSingle("VipPlayer", MessageKey.KICK_FOR_VIP)).willReturn("kick for vip");
        willAnswer(inv -> {
            ((Runnable) inv.getArgument(1)).run();
            return null;
        }).given(bukkitService).scheduleSyncTaskFromOptionallyAsyncTask(eq(onlinePlayers.get(1)), any(Runnable.class));

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
        PlayerLoginEvent event = createFullServerEvent(player);
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

    @ParameterizedTest(name = "isAuthAvailable={0}")
    @ValueSource(booleans = {true, false})
    void shouldNotKickPlayer(boolean isAuthAvailable) throws FailedVerificationException {
        if (!isAuthAvailable) {
            given(settings.getProperty(RestrictionSettings.KICK_NON_REGISTERED)).willReturn(false);
        }

        onJoinVerifier.checkKickNonRegistered(isAuthAvailable);
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

    @ParameterizedTest(name = "name={0}, stored={1}, real={2}, preventOtherCase={3}")
    @MethodSource("acceptedCasingCases")
    void shouldAcceptNameCasing(String name, String storedName, String realName, boolean preventOtherCase)
            throws FailedVerificationException {
        PlayerAuth auth = PlayerAuth.builder().name(storedName).realName(realName).build();
        given(settings.getProperty(RegistrationSettings.PREVENT_OTHER_CASE)).willReturn(preventOtherCase);

        onJoinVerifier.checkNameCasing(name, auth);

        verifyNoInteractions(dataSource);
    }

    private static Stream<Arguments> acceptedCasingCases() {
        return Stream.of(
            Arguments.of("Bobby", "bobby", "Bobby", true),
            Arguments.of("Test", "test", "TEST", false));
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

    @ParameterizedTest(name = "{0} has real name {1}")
    @MethodSource("realNameUpdates")
    void shouldUpdateStoredRealName(String storedName, String realName, String connectingName)
            throws FailedVerificationException {
        PlayerAuth auth = PlayerAuth.builder().name(storedName).realName(realName).build();
        given(settings.getProperty(RegistrationSettings.PREVENT_OTHER_CASE)).willReturn(true);

        onJoinVerifier.checkNameCasing(connectingName, auth);

        verify(dataSource).updateRealName(storedName, connectingName);
    }

    private static Stream<Arguments> realNameUpdates() {
        return Stream.of(
            Arguments.of("authme", "", "Authme"),
            Arguments.of("someone", "Player", "SOMEONE"));
    }

    @Test
    void shouldAcceptNameForUnregisteredAccount() throws FailedVerificationException {
        onJoinVerifier.checkNameCasing("MyPlayer", null);

        verifyNoInteractions(dataSource);
    }

    @Test
    void shouldAcceptNameThatIsNotOnline() throws FailedVerificationException {
        given(settings.getProperty(RestrictionSettings.FORCE_SINGLE_SESSION)).willReturn(true);
        given(bukkitService.getOnlinePlayers()).willReturn(Collections.emptyList());

        onJoinVerifier.checkSingleSession("bobby");

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
        onJoinVerifier.checkAntibot("Lacey", true);

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

        assertThrows(FailedVerificationException.class,
            () -> onJoinVerifier.checkAntibot(name, isAuthAvailable));
        verify(permissionsManager).hasPermissionOffline(name, PlayerStatePermission.BYPASS_ANTIBOT);
        verify(antiBotService).shouldKick();
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

    @ParameterizedTest(name = "{0}, authAvailable={2}, admitted={3}")
    @MethodSource("countryCheckCases")
    void shouldCheckPlayerCountry(String name, String ip, boolean authAvailable, boolean admitted)
            throws FailedVerificationException {
        given(settings.getProperty(ProtectionSettings.ENABLE_PROTECTION)).willReturn(true);
        if (authAvailable) {
            given(settings.getProperty(ProtectionSettings.ENABLE_PROTECTION_REGISTERED)).willReturn(true);
        }
        given(validationService.isCountryAdmitted(ip)).willReturn(admitted);

        if (admitted) {
            onJoinVerifier.checkPlayerCountry(name, ip, authAvailable);
            verify(validationService).isCountryAdmitted(ip);
        } else {
            expectValidationExceptionWith(() -> onJoinVerifier.checkPlayerCountry(name, ip, authAvailable),
                MessageKey.COUNTRY_BANNED_ERROR);
        }
    }

    private static Stream<Arguments> countryCheckCases() {
        return Stream.of(
            Arguments.of("lucas", "192.168.0.1", false, true),
            Arguments.of("gabriel", "192.168.10.24", true, true),
            Arguments.of("bob", "192.168.40.0", false, false));
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

        FailedVerificationException exception = assertThrows(FailedVerificationException.class,
            () -> onJoinVerifier.checkNameRestrictions(name, address));
        assertThat(exception.getReason(), equalTo(MessageKey.NOT_OWNER_ERROR));
        verify(bukkitService).runOnGlobalRegion(any(Runnable.class));
    }

    private static PlayerLoginEvent createFullServerEvent(Player player) {
        PlayerLoginEvent event = new PlayerLoginEvent(player, "hostname", null);
        event.setResult(PlayerLoginEvent.Result.KICK_FULL);
        return event;
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
