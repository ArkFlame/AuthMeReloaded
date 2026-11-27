package fr.xephi.authme.permission;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

/**
 * Test for {@link PermissionsSystemType}.
 */
class PermissionsSystemTypeTest {

    @Test
    void shouldHaveDefinedAndUniqueNames() {
        Set<String> names = new HashSet<>();

        for (PermissionsSystemType system : PermissionsSystemType.values()) {
            assertThat("Plugin name for enum entry '" + system + "' is not null",
                system.getPluginName(), not(nullValue()));
            assertThat("Name for enum entry '" + system + "' is consistent",
                system.toString(), equalTo(system.getPluginName()));
            assertThat("Only one enum entry has plugin name '" + system.getPluginName() + "'",
                names, not(hasItem(system.getPluginName())));
            names.add(system.getPluginName());
        }
    }

    @Test
    void shouldRecognizePermissionSystemType() {
        assertThat(PermissionsSystemType.isPermissionSystem("bogus"), equalTo(false));
        assertThat(PermissionsSystemType.isPermissionSystem("LuckPerms"), equalTo(true));
    }

}
