package fr.xephi.authme.process.login;

import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.data.auth.PlayerCache;
import fr.xephi.authme.data.limbo.LimboPlayer;
import fr.xephi.authme.data.limbo.LimboService;
import fr.xephi.authme.permission.PermissionsManager;
import fr.xephi.authme.permission.PlayerStatePermission;
import fr.xephi.authme.platform.DialogAdapter;
import fr.xephi.authme.service.BukkitService;
import fr.xephi.authme.service.CommonService;
import fr.xephi.authme.service.DialogStateService;
import fr.xephi.authme.service.JoinMessageService;
import fr.xephi.authme.service.TeleportationService;
import fr.xephi.authme.service.bungeecord.BungeeSender;
import fr.xephi.authme.settings.WelcomeMessageConfiguration;
import fr.xephi.authme.settings.commandconfig.CommandManager;
import fr.xephi.authme.settings.properties.RegistrationSettings;
import fr.xephi.authme.settings.properties.RestrictionSettings;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Test for {@link ProcessSyncPlayerLogin}.
 */
@ExtendWith(MockitoExtension.class)
class ProcessSyncPlayerLoginTest {

    @InjectMocks
    private ProcessSyncPlayerLogin processSyncPlayerLogin;

    @Mock
    private DialogAdapter dialogAdapter;
    @Mock
    private DialogStateService dialogStateService;
    @Mock
    private BungeeSender bungeeSender;
    @Mock
    private LimboService limboService;
    @Mock
    private BukkitService bukkitService;
    @Mock
    private TeleportationService teleportationService;
    @Mock
    private PlayerCache playerCache;
    @Mock
    private CommandManager commandManager;
    @Mock
    private CommonService commonService;
    @Mock
    private WelcomeMessageConfiguration welcomeMessageConfiguration;
    @Mock
    private JoinMessageService joinMessageService;
    @Mock
    private PermissionsManager permissionsManager;

    private Player player;

    @BeforeEach
    void setUpPlayer() {
        player = mock(Player.class);
        given(player.getName()).willReturn("Bobby");
        given(commonService.getProperty(RestrictionSettings.PROTECT_INVENTORY_BEFORE_LOGIN)).willReturn(false);
    }

    @ParameterizedTest(name = "limbo player present={0}")
    @ValueSource(booleans = {true, false})
    void shouldRestoreLimboStateWhenLoggedIn(boolean hasLimboPlayer) {
        given(limboService.getLimboPlayer(player)).willReturn(hasLimboPlayer ? mock(LimboPlayer.class) : null);

        processSyncPlayerLogin.processPlayerLogin(player, false, Collections.emptyList());

        if (hasLimboPlayer) {
            verify(limboService).restoreData(player);
            verify(limboService, never()).restorePendingOperator(player);
        } else {
            verify(limboService).restorePendingOperator(player);
            verify(limboService).restoreSpeedsForAutoLogin(player);
            verify(limboService, never()).restoreData(player);
        }
    }

    @ParameterizedTest(name = "dialog open={0}")
    @ValueSource(booleans = {true, false})
    void shouldCloseDialogOnlyWhenClearDialogOpenReturnsTrue(boolean dialogOpen) {
        given(dialogStateService.clearDialogOpen(player)).willReturn(dialogOpen);

        processSyncPlayerLogin.processPlayerLogin(player, false, Collections.emptyList());

        if (dialogOpen) {
            verify(dialogAdapter).closeDialog(player);
        } else {
            verify(dialogAdapter, never()).closeDialog(player);
        }
    }

    @Test
    void shouldNotSendBungeeRedirectForProxyInitiatedLogin() {
        given(playerCache.getAuth("bobby")).willReturn(PlayerAuth.builder().name("bobby").build());
        given(commonService.getProperty(RegistrationSettings.APPLY_BLIND_EFFECT)).willReturn(false);

        processSyncPlayerLogin.processPlayerLoginFromProxy(player, false, Collections.emptyList());

        verify(bungeeSender, never()).connectPlayerOnLogin(player);
        verify(permissionsManager, never()).hasPermission(player, PlayerStatePermission.BYPASS_BUNGEE_SEND);
    }

    @ParameterizedTest(name = "bypass permission={0}")
    @ValueSource(booleans = {true, false})
    void shouldSendBungeeRedirectOnlyWithoutBypassPermission(boolean hasBypassPermission) {
        given(playerCache.getAuth("bobby")).willReturn(PlayerAuth.builder().name("bobby").build());
        given(commonService.getProperty(RegistrationSettings.APPLY_BLIND_EFFECT)).willReturn(false);
        given(permissionsManager.hasPermission(player, PlayerStatePermission.BYPASS_BUNGEE_SEND))
            .willReturn(hasBypassPermission);

        processSyncPlayerLogin.processPlayerLogin(player, false, Collections.emptyList());

        if (hasBypassPermission) {
            verify(bungeeSender, never()).connectPlayerOnLogin(player);
        } else {
            verify(bungeeSender).connectPlayerOnLogin(player);
        }
    }
}
