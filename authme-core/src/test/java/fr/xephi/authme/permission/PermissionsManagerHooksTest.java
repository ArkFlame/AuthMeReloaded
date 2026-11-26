package fr.xephi.authme.permission;

import fr.xephi.authme.ReflectionTestUtils;
import fr.xephi.authme.TestHelper;
import fr.xephi.authme.data.limbo.UserGroup;
import fr.xephi.authme.permission.handlers.PermissionHandler;
import fr.xephi.authme.settings.Settings;
import fr.xephi.authme.settings.properties.PluginSettings;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Focused regression tests for hook updates and bulk group changes in {@link PermissionsManager}.
 */
class PermissionsManagerHooksTest {

    private final Settings settings = mock(Settings.class);
    private final PermissionsManager permissionsManager =
        new PermissionsManager(mock(Server.class), mock(PluginManager.class), settings);

    @BeforeAll
    static void setUpLogger() {
        TestHelper.setupLogger();
    }

    @Test
    void shouldUnhookWhenHookedPermissionPluginIsDisabled() {
        // given: a handler is hooked, but no permission plugin is available any more
        given(settings.getProperty(PluginSettings.FORCE_VAULT_HOOK)).willReturn(false);
        PermissionHandler staleHandler = mock(PermissionHandler.class);
        ReflectionTestUtils.setField(PermissionsManager.class, permissionsManager, "handler", staleHandler);
        assertThat(permissionsManager.isEnabled(), is(true));

        // when
        permissionsManager.onPluginDisable(PermissionsSystemType.VAULT.getPluginName());

        // then
        assertThat(permissionsManager.isEnabled(), is(false));
    }

    @Test
    void shouldSkipGroupsWithoutNameInBulkChanges() {
        // given
        PermissionHandler handler = mock(PermissionHandler.class);
        ReflectionTestUtils.setField(PermissionsManager.class, permissionsManager, "handler", handler);
        OfflinePlayer player = mock(OfflinePlayer.class);
        UserGroup valid = new UserGroup("valid");
        given(handler.addToGroup(player, valid)).willReturn(true);

        // when
        boolean result = permissionsManager.addGroups(player, Arrays.asList(
            new UserGroup(null), new UserGroup(""), valid, null));

        // then
        assertThat(result, is(true));
        verify(handler, times(1)).addToGroup(player, valid);
        verifyNoMoreInteractions(handler);
    }
}
