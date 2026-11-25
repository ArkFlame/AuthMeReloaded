package fr.xephi.authme.data.limbo;

import fr.xephi.authme.TestHelper;
import fr.xephi.authme.permission.PermissionsManager;
import fr.xephi.authme.settings.Settings;
import fr.xephi.authme.settings.properties.PluginSettings;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test for {@link AuthGroupHandler} covering issue #3102: temporary groups in the limbo
 * snapshot must not prevent the other groups from being restored or removed.
 */
@ExtendWith(MockitoExtension.class)
class AuthGroupHandlerTest {

    @Mock
    private PermissionsManager permissionsManager;

    @Mock
    private Settings settings;

    @InjectMocks
    private AuthGroupHandler authGroupHandler;

    @BeforeAll
    static void initLogger() {
        TestHelper.setupLogger();
    }

    @BeforeEach
    void setUp() {
        when(settings.getProperty(PluginSettings.ENABLE_PERMISSION_CHECK)).thenReturn(true);
        when(settings.getProperty(PluginSettings.REGISTERED_GROUP)).thenReturn("registered");
        when(settings.getProperty(PluginSettings.UNREGISTERED_GROUP)).thenReturn("unregistered");
        when(permissionsManager.hasGroupSupport()).thenReturn(true);
        authGroupHandler.reload();
    }

    @Test
    void shouldRestorePermanentGroupsWhenTemporaryGroupIsRejected() {
        // given
        Player player = mock(Player.class);
        UserGroup temporaryGroup = new UserGroup("vip-temp");
        UserGroup permanentGroup = new UserGroup("member");
        LimboPlayer limbo = mock(LimboPlayer.class);
        when(limbo.getGroups()).thenReturn(Arrays.asList(temporaryGroup, permanentGroup));
        // LuckPerms rejects the expired temporary group with an IllegalArgumentException
        lenient().doThrow(new IllegalArgumentException("group name is invalid"))
            .when(permissionsManager).addGroup(player, temporaryGroup);
        // The old implementation passed the whole snapshot to addGroups at once and aborted there
        lenient().doThrow(new IllegalArgumentException("group name is invalid"))
            .when(permissionsManager).addGroups(eq(player), anyCollection());

        // when
        authGroupHandler.setGroup(player, limbo, AuthGroupType.LOGGED_IN);

        // then
        verify(permissionsManager).addGroup(player, permanentGroup);
        verify(permissionsManager, never()).addGroups(eq(player), anyCollection());
    }

    @Test
    void shouldRemovePermanentGroupsWhenTemporaryGroupIsRejected() {
        // given
        Player player = mock(Player.class);
        UserGroup temporaryGroup = new UserGroup("vip-temp");
        UserGroup permanentGroup = new UserGroup("member");
        LimboPlayer limbo = mock(LimboPlayer.class);
        when(limbo.getGroups()).thenReturn(Arrays.asList(temporaryGroup, permanentGroup));
        lenient().doThrow(new IllegalArgumentException("group name is invalid"))
            .when(permissionsManager).removeGroup(player, temporaryGroup);
        lenient().doThrow(new IllegalArgumentException("group name is invalid"))
            .when(permissionsManager).removeGroups(eq(player), anyCollection());

        // when
        authGroupHandler.setGroup(player, limbo, AuthGroupType.REGISTERED_UNAUTHENTICATED);

        // then
        verify(permissionsManager).removeGroup(player, permanentGroup);
        verify(permissionsManager, never()).removeGroups(eq(player), anyCollection());
    }

    @Test
    void shouldSkipGroupsWithoutNameWhenRestoring() {
        // given
        Player player = mock(Player.class);
        LimboPlayer limbo = mock(LimboPlayer.class);
        when(limbo.getGroups()).thenReturn(Collections.singletonList(new UserGroup(null)));

        // when
        authGroupHandler.setGroup(player, limbo, AuthGroupType.LOGGED_IN);

        // then
        verify(permissionsManager, never()).addGroup(eq(player), any(UserGroup.class));
    }
}
