package fr.xephi.authme.permission;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Has common tests for enums implementing {@link PermissionNode}.
 */
public abstract class AbstractPermissionsEnumTest {

    @Test
    void shouldAllStartWitRequiredPrefix() {
        String requiredPrefix = getRequiredPrefix();

        for (PermissionNode permission : getPermissionNodes()) {
            if (!permission.getNode().startsWith(requiredPrefix)) {
                fail("The permission '" + permission + "' does not start with the required prefix '"
                    + requiredPrefix + "'");
            }
        }
    }

    @Test
    void shouldHaveUniqueNodes() {
        Set<String> nodes = new HashSet<>();

        for (PermissionNode permission : getPermissionNodes()) {
            if (!nodes.add(permission.getNode())) {
                fail("More than one enum value defines the node '" + permission.getNode() + "'");
            }
        }
    }

    /**
     * @return the permission nodes to test
     */
    protected abstract PermissionNode[] getPermissionNodes();

    /**
     * @return text with which all permission nodes must start with
     */
    protected abstract String getRequiredPrefix();

}
