package fr.xephi.authme.permission;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.google.common.collect.Sets.newHashSet;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Test for {@link PlayerStatePermission}.
 */
class PlayerStatePermissionTest extends AbstractPermissionsEnumTest {

    @Test
    void shouldNotStartWithOtherPrefixes() {
        Set<String> forbiddenPrefixes = newHashSet("authme.player", "authme.admin", "authme.debug");

        for (PlayerStatePermission permission : PlayerStatePermission.values()) {
            for (String prefix : forbiddenPrefixes) {
                if (permission.getNode().startsWith(prefix)) {
                    fail("The permission '" + permission + "' should not start with any of " + forbiddenPrefixes);
                }
            }
        }
    }

    @Override
    protected PermissionNode[] getPermissionNodes() {
        return PlayerStatePermission.values();
    }

    @Override
    protected String getRequiredPrefix() {
        return "authme.";
    }
}
