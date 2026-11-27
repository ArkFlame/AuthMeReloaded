package fr.xephi.authme;

import fr.xephi.authme.permission.AdminPermission;
import fr.xephi.authme.permission.DebugSectionPermissions;
import fr.xephi.authme.permission.PermissionNode;
import fr.xephi.authme.permission.PlayerPermission;
import fr.xephi.authme.permission.PlayerStatePermission;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks that the {@code plugin.yml} files shipped by every server jar declare the same permissions,
 * and that all permission nodes defined in code are present in those files.
 *
 * <p>Permission names are read by parsing the two-space-indented keys below the {@code permissions:}
 * section. This keeps the test independent of whether SnakeYAML is on the test classpath.</p>
 */
class PluginYmlConsistencyTest {

    /** plugin.yml locations relative to the authme-core module directory. */
    private static final List<String> PLUGIN_YML_PATHS = Collections.unmodifiableList(Arrays.asList(
        "../authme-spigot-1.8/src/main/resources/plugin.yml",
        "../authme-spigot/src/main/resources/plugin.yml"));

    @Test
    void shouldDeclareIdenticalPermissionsInAllJars() throws IOException {
        // given
        List<Set<String>> permissionsPerJar = readPermissionsFromAllJars();

        // when / then
        Set<String> reference = permissionsPerJar.get(0);
        for (int i = 1; i < permissionsPerJar.size(); i++) {
            assertEquals(reference, permissionsPerJar.get(i),
                "Permission declarations differ between " + PLUGIN_YML_PATHS.get(0)
                    + " and " + PLUGIN_YML_PATHS.get(i));
        }
    }

    @Test
    void shouldHaveAllCodePermissionsInPluginYml() throws IOException {
        // given
        Set<String> declared = readPermissionsFromAllJars().get(0);
        Set<String> codeNodes = gatherCodePermissionNodes();

        // when
        Set<String> missing = new HashSet<>(codeNodes);
        missing.removeAll(declared);

        // then
        assertTrue(missing.isEmpty(), "Permission nodes from code missing in plugin.yml: " + missing);
    }

    /**
     * Reads the permission names from every known plugin.yml, skipping the test when any file is absent.
     *
     * @return the permission names declared by each jar, in the same order as {@link #PLUGIN_YML_PATHS}
     * @throws IOException if a file cannot be read
     */
    private static List<Set<String>> readPermissionsFromAllJars() throws IOException {
        List<Set<String>> result = new ArrayList<>(PLUGIN_YML_PATHS.size());
        for (String path : PLUGIN_YML_PATHS) {
            File file = new File(path);
            Assumptions.assumeTrue(file.isFile(), "Skipping: plugin.yml not found at " + file.getPath());
            result.add(readPermissionNames(file));
        }
        return result;
    }

    /**
     * Extracts the permission node names from the {@code permissions:} section of a plugin.yml file.
     * Permission entries are keys indented by exactly two spaces; their fields and children are indented further.
     *
     * @param file the plugin.yml file to parse
     * @return the set of declared permission names
     * @throws IOException if the file cannot be read
     */
    private static Set<String> readPermissionNames(File file) throws IOException {
        Set<String> names = new HashSet<>();
        boolean inPermissions = false;
        for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
            if (line.startsWith("permissions:")) {
                inPermissions = true;
            } else if (inPermissions && !line.trim().isEmpty() && !line.startsWith(" ")) {
                // A new top-level key marks the end of the permissions section.
                break;
            } else if (inPermissions && line.startsWith("  ") && !line.startsWith("   ")
                && line.trim().endsWith(":")) {
                String entry = line.trim();
                names.add(entry.substring(0, entry.length() - 1));
            }
        }
        return names;
    }

    /**
     * Gathers every permission node value from the permission enums defined in the codebase.
     *
     * @return all permission nodes defined in code
     */
    private static Set<String> gatherCodePermissionNodes() {
        Set<String> nodes = new HashSet<>();
        addNodes(nodes, PlayerStatePermission.values());
        addNodes(nodes, AdminPermission.values());
        addNodes(nodes, PlayerPermission.values());
        addNodes(nodes, DebugSectionPermissions.values());
        return nodes;
    }

    private static void addNodes(Set<String> target, PermissionNode[] values) {
        for (PermissionNode value : values) {
            target.add(value.getNode());
        }
    }
}
