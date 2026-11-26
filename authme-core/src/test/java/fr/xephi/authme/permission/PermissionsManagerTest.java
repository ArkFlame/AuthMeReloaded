package fr.xephi.authme.permission;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * Test for {@link PermissionsManager}.
 */
@ExtendWith(MockitoExtension.class)
class PermissionsManagerTest {

    @InjectMocks
    private PermissionsManager permissionsManager;

    @ParameterizedTest(name = "{0}")
    @MethodSource("createParameters")
    void shouldEvaluateDefaultPermission(String description, boolean player, PermissionNode node,
                                         boolean op, boolean expected) {
        CommandSender sender = player ? mock(Player.class) : mock(CommandSender.class);
        if (op) {
            given(sender.isOp()).willReturn(true);
        }

        boolean result = permissionsManager.hasPermission(sender, node);

        assertThat(result, equalTo(expected));
    }

    private static Stream<Arguments> createParameters() {
        return Stream.of(
            Arguments.of("default permission for command sender", false, TestPermissions.LOGIN, false, true),
            Arguments.of("op command sender", false, TestPermissions.DELETE_USER, true, true),
            Arguments.of("deny NOT_ALLOWED for command sender", false, TestPermissions.WORLD_DOMINATION, false, false),
            Arguments.of("null permission for command sender", false, null, false, true),
            Arguments.of("default permission for player", true, TestPermissions.LOGIN, false, true),
            Arguments.of("deny for non-op player", true, TestPermissions.DELETE_USER, false, false),
            Arguments.of("allow for op player", true, TestPermissions.DELETE_USER, true, true),
            Arguments.of("deny NOT_ALLOWED for player", true, TestPermissions.WORLD_DOMINATION, false, false),
            Arguments.of("null permission for player", true, null, false, true));
    }
}
