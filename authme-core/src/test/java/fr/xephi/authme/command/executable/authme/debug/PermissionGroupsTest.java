package fr.xephi.authme.command.executable.authme.debug;

import fr.xephi.authme.data.limbo.UserGroup;
import fr.xephi.authme.permission.PermissionsManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockMakers;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.hamcrest.MockitoHamcrest.argThat;

/**
 * Test for {@link PermissionGroups}.
 */
@ExtendWith(MockitoExtension.class)
class PermissionGroupsTest {

    @InjectMocks
    private PermissionGroups permissionGroups;

    @Mock(mockMaker = MockMakers.INLINE)
    private PermissionsManager permissionsManager;

    @Test
    void shouldShowPrimaryGroupSeparately() {
        // given
        String playerName = "bobby";
        Player player = mock(Player.class, withSettings().mockMaker(MockMakers.INLINE));
        given(permissionsManager.getGroups(player)).willReturn(
            Arrays.asList(new UserGroup("admin"), new UserGroup("default")));
        given(permissionsManager.hasGroupSupport()).willReturn(true);
        given(permissionsManager.getPrimaryGroup(player)).willReturn(new UserGroup("admin"));
        CommandSender sender = mock(CommandSender.class, withSettings().mockMaker(MockMakers.INLINE));

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class, withSettings().mockMaker(MockMakers.INLINE))) {
            bukkit.when(() -> Bukkit.getPlayer(playerName)).thenReturn(player);

            // when
            permissionGroups.execute(sender, Collections.singletonList(playerName));
        }

        // then
        verify(sender).sendMessage(argThat(containsString("permission groups: admin, default")));
        verify(sender).sendMessage(argThat(containsString("Primary group is: admin")));
    }
}
