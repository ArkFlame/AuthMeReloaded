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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.function.Function;

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
        // given
        Player player = givenSession(NAME, IP, playerAuth(NAME, System.currentTimeMillis() - 10 * 60 * 1000, IP));
        given(commonService.getProperty(PluginSettings.SESSIONS_TIMEOUT)).willReturn(8);

        // when
        boolean result = sessionService.canResumeSession(player);

        // then
        assertThat(result, equalTo(false));
        verify(dataSource).setUnlogged(NAME);
        verify(dataSource).revokeSession(NAME);
    }

    @Test
    void shouldRefuseSessionForAuthWithNullLastLoginTimestamp() {
        // given
        Player player = givenSession(NAME, IP, playerAuth(NAME, null, IP));

        // when
        boolean result = sessionService.canResumeSession(player);

        // then
        assertThat(result, equalTo(false));
        verify(dataSource).setUnlogged(NAME);
        verify(dataSource).revokeSession(NAME);
    }

    @Test
    void shouldCheckLastLoginIp() {
        // given
        Player player = givenSession(NAME, IP, playerAuth(NAME, System.currentTimeMillis() - 7 * 60 * 1000, "8.8.8.8"));
        given(commonService.getProperty(PluginSettings.SESSIONS_TIMEOUT)).willReturn(8);

        // when
        boolean result = sessionService.canResumeSession(player);

        // then
        assertThat(result, equalTo(false));
        verify(commonService).send(player, MessageKey.SESSION_EXPIRED);
        verify(dataSource).setUnlogged(NAME);
        verify(dataSource).revokeSession(NAME);
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
    void shouldHandleNullPlayerAuth() {
        // given
        Player player = givenSession(NAME, IP, null);

        // when
        boolean result = sessionService.canResumeSession(player);

        // then
        assertThat(result, equalTo(false));
        verify(dataSource).setUnlogged(NAME);
        verify(dataSource).revokeSession(NAME);
    }

    @Test
    void shouldHandlePlayerAuthWithNullLastIp() {
        // given
        Player player = givenSession("Charles", "144.117.118.145",
            playerAuth("Charles", System.currentTimeMillis() - 2, null));
        given(commonService.getProperty(PluginSettings.SESSIONS_TIMEOUT)).willReturn(8);

        // when
        boolean result = sessionService.canResumeSession(player);

        // then
        assertThat(result, equalTo(false));
        verify(dataSource).setUnlogged("Charles");
        verify(dataSource).revokeSession("Charles");
    }

    @Test
    void shouldNotResumeSessionForPlayerWithDisabledSessionPermission() {
        // given
        Player player = mock(Player.class);
        given(player.getName()).willReturn(NAME);
        given(dataSource.hasSession(NAME)).willReturn(true);
        given(permissionsManager.hasPermission(player, PlayerStatePermission.DISABLE_SESSION)).willReturn(true);

        // when
        boolean result = sessionService.canResumeSession(player);

        // then
        assertThat(result, equalTo(false));
        verify(dataSource).hasSession(NAME);
        verify(dataSource).setUnlogged(NAME);
        verify(dataSource).revokeSession(NAME);
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

    @Test
    void shouldNotReportValidSessionForPlayerWithDisabledSessionPermission() {
        // given
        given(dataSource.hasSession("Bobby")).willReturn(true);
        given(permissionsManager.hasPermissionOffline("Bobby", PlayerStatePermission.DISABLE_SESSION))
            .willReturn(true);

        // when
        boolean result = sessionService.hasValidSession("Bobby", "127.3.12.15");

        // then
        assertThat(result, equalTo(false));
        verify(dataSource, only()).hasSession("Bobby");
    }

    @Test
    void shouldReportValidSessionForPlayerWithoutDisabledSessionPermission() {
        // given
        given(dataSource.hasSession(NAME)).willReturn(true);
        given(commonService.getProperty(PluginSettings.SESSIONS_TIMEOUT)).willReturn(8);
        given(dataSource.getAuth(NAME)).willReturn(playerAuth(NAME, System.currentTimeMillis() - 60 * 1000, IP));

        // when
        boolean result = sessionService.hasValidSession(NAME, IP);

        // then
        assertThat(result, equalTo(true));
        verify(permissionsManager).hasPermissionOffline(NAME, PlayerStatePermission.DISABLE_SESSION);
    }

    @Test
    void shouldNotGrantSessionToPlayerWithDisabledSessionPermission() {
        // given
        Player player = mock(Player.class);
        given(permissionsManager.hasPermission(player, PlayerStatePermission.DISABLE_SESSION)).willReturn(true);

        // when
        sessionService.grantSession(player);

        // then
        verifyNoInteractions(dataSource);
    }

    @Test
    void shouldGrantSessionToPlayerWithoutDisabledSessionPermission() {
        // given
        Player player = mock(Player.class);
        given(player.getName()).willReturn("bobby");

        // when
        sessionService.grantSession(player);

        // then
        verify(dataSource, only()).grantSession("bobby");
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
