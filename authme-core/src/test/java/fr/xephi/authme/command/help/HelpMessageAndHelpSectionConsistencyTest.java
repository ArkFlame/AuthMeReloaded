package fr.xephi.authme.command.help;

import fr.xephi.authme.util.StringUtils;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Test for enums {@link HelpMessage} and {@link HelpSection}.
 */
class HelpMessageAndHelpSectionConsistencyTest {

    @Test
    void shouldHaveUniqueNonEmptyKeys() {
        Set<String> keys = new HashSet<>();
        assertKeys(keys, "message", HelpMessage.values());
        assertKeys(keys, "section", HelpSection.values());
    }

    private static void assertKeys(Set<String> keys, String type, HelpTextKey[] values) {
        for (HelpTextKey key : values) {
            assertThat("Key for " + type + " '" + key + "' is empty",
                StringUtils.isBlank(key.getKey()), equalTo(false));
            if (!keys.add(key.getKey())) {
                fail("Key for " + type + " '" + key + "' is already used elsewhere");
            }
        }
    }
}
