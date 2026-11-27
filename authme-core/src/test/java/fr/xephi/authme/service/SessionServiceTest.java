package fr.xephi.authme.service;

import fr.xephi.authme.TestHelper;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.events.RestoreSessionEvent;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.permission.PermissionsManager;
import fr.xephi.authme.permission.PlayerStatePermission;
import fr.xephi.authme.settings.properties.PluginSettings;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.function.Function;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Test for {@link SessionService}.
 */
@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    private static final String NAME = "Bobby";
    private static final String IP = "127.3.12.15";

    private SessionService sessionService;

    @Mock
    private DataSource dataSource;
    @Mock
    private CommonService commonService;
    @Mock
    private BukkitService bukkitService;
    @Mock
    private PermissionsManager permissionsManager;

    @BeforeAll
    static void initLogger() {
        TestHelper.setupLogger();
    }

    @BeforeEach
    void createSessionService() {
        given(commonService.getProperty(PluginSettings.SESSIONS_ENABLED)).willReturn(true);
        sessionService = new SessionService(commonService, bukkitService, dataSource, permissionsManager, null);
    }

    @Test
    void shouldCheckSessionsEnabledSetting() {
        // given
        Player player = mock(Player.class);
        given(commonService.getProperty(PluginSettings.SESSIONS_ENABLED)).willReturn(false);
        sessionService.reload();

        // when
        boolean result = sessionService.canResumeSession(player);

        // then
        assertThat(result, equalTo(false));
        verifyNoInteractions(dataSource);
    }

    @Test
    void shouldCheckIfUserHasSession() {
        // given
        Player player = mock(Player.class);
        given(player.getName()).willReturn(NAME);
        given(dataSource.hasSession(NAME)).willReturn(false);

        // when
        boolean result = sessionService.canResumeSession(player);

        // then
        assertThat(result, equalTo(false));
        verify(commonService).getProperty(PluginSettings.SESSIONS_ENABLED);
        verifyNoMoreInteractions(commonService);
        verify(dataSource, only()).hasSession(NAME);
    }

    @Test
    void shouldCheckLastLoginDate() {
        Player player = givenSession(NAME, IP, playerAuth(NAME, System.currentTimeMillis() - 10 * 60 * 1000, IP));
        given(commonService.getProperty(PluginSettings.SESSIONS_TIMEOUT)).willReturn(8);

        assertSessionRefused(player, NAME);
    }

    @ParameterizedTest
    @MethodSource("invalidAuths")
    void shouldRefuseSessionWithoutValidAuth(PlayerAuth auth) {
        Player player = givenSession(NAME, IP, auth);

        assertSessionRefused(player, NAME);
    }

    @Test
    void shouldCheckLastLoginIp() {
        Player player = givenSession(NAME, IP, playerAuth(NAME, System.currentTimeMillis() - 7 * 60 * 1000, "8.8.8.8"));
        given(commonService.getProperty(PluginSettings.SESSIONS_TIMEOUT)).willReturn(8);

        assertSessionRefused(player, NAME);
        verify(commonService).send(player, MessageKey.SESSION_EXPIRED);
    }

    @Test
    void shouldEmitEventForValidSession() {
        // given
        Player player = givenSession(NAME, IP, playerAuth(NAME, System.currentTimeMillis() - 7 * 60 * 1000, IP));
        given(commonService.getProperty(PluginSettings.SESSIONS_TIMEOUT)).willReturn(8);
        RestoreSessionEvent event = spy(new RestoreSessionEvent(player, false));
        given(bukkitService.createAndCallEvent(any(Function.class))).willReturn(event);

        // when
        boolean result = sessionService.canResumeSession(player);

        // then
        assertThat(result, equalTo(true));
        verify(commonService).getProperty(PluginSettings.SESSIONS_ENABLED);
        verify(commonService).getProperty(PluginSettings.SESSIONS_TIMEOUT);
        verifyNoMoreInteractions(commonService);
        verify(dataSource).setUnlogged(NAME);
        verify(dataSource).revokeSession(NAME);
        verify(event).isCancelled();
    }

    @Test
    void shouldHandlePlayerAuthWithNullLastIp() {
        Player player = givenSession("Charles", "144.117.118.145",
            playerAuth("Charles", System.currentTimeMillis() - 2, null));
        given(commonService.getProperty(PluginSettings.SESSIONS_TIMEOUT)).willReturn(8);

        assertSessionRefused(player, "Charles");
    }

    @Test
    void shouldNotResumeSessionForPlayerWithDisabledSessionPermission() {
        // given
        Player player = mock(Player.class);
        given(player.getName()).willReturn(NAME);
        given(dataSource.hasSession(NAME)).willReturn(true);
        given(permissionsManager.hasPermission(player, PlayerStatePermission.DISABLE_SESSION)).willReturn(true);

        // when
        assertSessionRefused(player, NAME);

        // then
        verify(dataSource).hasSession(NAME);
        verifyNoMoreInteractions(dataSource);
        verifyNoInteractions(bukkitService);
    }

    @Test
    void shouldResumeSessionForPlayerWithoutDisabledSessionPermission() {
        // given
        Player player = givenSession(NAME, IP, playerAuth(NAME, System.currentTimeMillis() - 60 * 1000, IP));
        given(commonService.getProperty(PluginSettings.SESSIONS_TIMEOUT)).willReturn(8);
        given(bukkitService.createAndCallEvent(any(Function.class))).willReturn(new RestoreSessionEvent(player, false));

        // when
        boolean result = sessionService.canResumeSession(player);

        // then
        assertThat(result, equalTo(true));
    }

    @ParameterizedTest(name = "hasValidSession with disabled-session permission: {0}")
    @ValueSource(booleans = {true, false})
    void shouldReportValidSessionDependingOnPermission(boolean hasDisabledSessionPermission) {
        given(dataSource.hasSession(NAME)).willReturn(true);
        if (hasDisabledSessionPermission) {
            given(permissionsManager.hasPermissionOffline(NAME, PlayerStatePermission.DISABLE_SESSION))
                .willReturn(true);
        } else {
            given(commonService.getProperty(PluginSettings.SESSIONS_TIMEOUT)).willReturn(8);
            given(dataSource.getAuth(NAME)).willReturn(playerAuth(NAME, System.currentTimeMillis() - 60 * 1000, IP));
        }

        boolean result = sessionService.hasValidSession(NAME, IP);

        assertThat(result, equalTo(!hasDisabledSessionPermission));
        if (hasDisabledSessionPermission) {
            verify(dataSource, only()).hasSession(NAME);
        } else {
            verify(permissionsManager).hasPermissionOffline(NAME, PlayerStatePermission.DISABLE_SESSION);
        }
    }

    @ParameterizedTest(name = "disabled-session permission: {0}")
    @ValueSource(booleans = {true, false})
    void shouldGrantSessionDependingOnPermission(boolean hasDisabledSessionPermission) {
        Player player = mock(Player.class);
        if (hasDisabledSessionPermission) {
            given(permissionsManager.hasPermission(player, PlayerStatePermission.DISABLE_SESSION)).willReturn(true);
        } else {
            given(player.getName()).willReturn("bobby");
        }

        sessionService.grantSession(player);

        if (hasDisabledSessionPermission) {
            verifyNoInteractions(dataSource);
        } else {
            verify(dataSource, only()).grantSession("bobby");
        }
    }

    @ParameterizedTest
    @MethodSource("invalidTimestamps")
    void shouldRefuseSessionForInvalidLastLoginTimestamp(long lastLogin) {
        Player player = givenSession(NAME, IP, playerAuth(NAME, lastLogin, IP));

        assertSessionRefused(player, NAME);
    }

    @Test
    void shouldAcceptRecentSessionWithMaximalTimeoutWithoutOverflow() {
        // given
        Player player = givenSession(NAME, IP,
            playerAuth(NAME, System.currentTimeMillis() - 60 * 1000, IP));
        given(commonService.getProperty(PluginSettings.SESSIONS_TIMEOUT)).willReturn(Integer.MAX_VALUE);
        given(bukkitService.createAndCallEvent(any(Function.class)))
            .willReturn(new RestoreSessionEvent(player, false));

        // when
        boolean result = sessionService.canResumeSession(player);

        // then
        assertThat(result, equalTo(true));
    }

    private void assertSessionRefused(Player player, String expectedName) {
        assertThat(sessionService.canResumeSession(player), equalTo(false));
        verify(dataSource).setUnlogged(expectedName);
        verify(dataSource).revokeSession(expectedName);
    }

    private static Stream<Arguments> invalidAuths() {
        return Stream.of(
            Arguments.of(playerAuth(NAME, null, IP)),
            Arguments.of((PlayerAuth) null));
    }

    private static Stream<Arguments> invalidTimestamps() {
        return Stream.of(
            Arguments.of(-1L),
            Arguments.of(Long.MIN_VALUE),
            Arguments.of(System.currentTimeMillis() + 60 * 1000));
    }

    private static PlayerAuth playerAuth(String name, Long lastLogin, String lastIp) {
        return PlayerAuth.builder().name(name).lastLogin(lastLogin).lastIp(lastIp).build();
    }

    private Player givenSession(String name, String ip, PlayerAuth auth) {
        Player player = TestPlayerFactory.mockPlayerWithNameAndIp(name, ip);
        given(dataSource.hasSession(name)).willReturn(true);
        given(dataSource.getAuth(name)).willReturn(auth);
        return player;
    }
}
