package fr.xephi.authme.service;

import fr.xephi.authme.TestHelper;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.events.RestoreSessionEvent;
import fr.xephi.authme.permission.PermissionsManager;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Verifies that a pending premium enrollment suspends session login (#3129).
 */
@ExtendWith(MockitoExtension.class)
class SessionServicePendingPremiumTest {

    private SessionService sessionService;

    @Mock
    private DataSource dataSource;
    @Mock
    private CommonService commonService;
    @Mock
    private BukkitService bukkitService;
    @Mock
    private PermissionsManager permissionsManager;
    @Mock
    private PendingPremiumCache pendingPremiumCache;

    @BeforeAll
    static void initLogger() {
        TestHelper.setupLogger();
    }

    @BeforeEach
    void createSessionService() throws Exception {
        given(commonService.getProperty(PluginSettings.SESSIONS_ENABLED)).willReturn(true);
        sessionService = new SessionService(commonService, bukkitService, dataSource, permissionsManager, pendingPremiumCache);
    }

    @Test
    void shouldNotResumeSessionWhilePremiumIsPending() {
        // given
        String name = "Bobby";
        Player player = mock(Player.class);
        given(player.getName()).willReturn(name);
        given(pendingPremiumCache.isPending(name)).willReturn(true);

        // when
        boolean result = sessionService.canResumeSession(player);

        // then
        assertThat(result, equalTo(false));
        verify(pendingPremiumCache).isPending(name);
        verifyNoInteractions(dataSource);
    }

    @Test
    void shouldResumeSessionWhenNoPremiumIsPending() {
        // given
        String name = "Bobby";
        String ip = "127.3.12.15";
        Player player = TestPlayerFactory.mockPlayerWithNameAndIp(name, ip);
        given(commonService.getProperty(PluginSettings.SESSIONS_TIMEOUT)).willReturn(8);
        given(dataSource.hasSession(name)).willReturn(true);
        given(dataSource.getAuth(name)).willReturn(PlayerAuth.builder()
            .name(name)
            .lastLogin(System.currentTimeMillis() - 60 * 1000)
            .lastIp(ip).build());
        given(bukkitService.createAndCallEvent(any(Function.class)))
            .willReturn(new RestoreSessionEvent(player, false));

        // when
        boolean result = sessionService.canResumeSession(player);

        // then
        assertThat(result, equalTo(true));
        verify(pendingPremiumCache).isPending(name);
        verify(dataSource).setUnlogged(name);
        verify(dataSource).revokeSession(name);
    }

}
