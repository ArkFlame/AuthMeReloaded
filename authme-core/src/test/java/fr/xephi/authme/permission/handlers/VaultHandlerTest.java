package fr.xephi.authme.permission.handlers;

import fr.xephi.authme.data.limbo.UserGroup;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Test for {@link VaultHandler}.
 */
class VaultHandlerTest {

    private final VaultHandlerTestImpl vaultHandlerTest = VaultHandlerTestImpl.create();

    @ParameterizedTest(name = "{0}")
    @MethodSource("createParameters")
    void shouldGetGroups(String description, String[] vaultGroups, List<String> expectedGroups) {
        Player player = mock(Player.class);
        given(vaultHandlerTest.permissionMock.getPlayerGroups(null, player)).willReturn(vaultGroups);

        List<UserGroup> result = vaultHandlerTest.getGroups(player);

        assertThat(result.stream().map(UserGroup::getGroupName).collect(toList()), equalTo(expectedGroups));
        verify(vaultHandlerTest.permissionMock).getPlayerGroups(null, player);
    }

    private static Stream<Arguments> createParameters() {
        return Stream.of(
            Arguments.of("should return groups", new String[]{"abc", "test"}, Arrays.asList("abc", "test")),
            Arguments.of("should handle null as groups", null, Collections.emptyList()));
    }

    @Test
    void shouldReturnNullForMissingPrimaryGroup() {
        Player player = mock(Player.class);
        given(vaultHandlerTest.permissionMock.getPrimaryGroup(null, player)).willReturn(null);

        UserGroup result = vaultHandlerTest.getPrimaryGroup(player);

        assertThat(result, nullValue());
        verify(vaultHandlerTest.permissionMock).getPrimaryGroup(null, player);
    }

    /** Test implementation using a mock Vault Permission instance. */
    private static final class VaultHandlerTestImpl extends VaultHandler {

        private Permission permissionMock;

        VaultHandlerTestImpl() throws PermissionHandlerException {
            super(null);
        }

        static VaultHandlerTestImpl create() {
            try {
                return new VaultHandlerTestImpl();
            } catch (PermissionHandlerException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        Permission getVaultPermission(Server server) {
            permissionMock = mock(Permission.class);
            return permissionMock;
        }
    }
}
