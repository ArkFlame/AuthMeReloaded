package fr.xephi.authme.message;

import fr.xephi.authme.util.StringUtils;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Test for {@link MessageKey}.
 */
class MessageKeyTest {

    @Test
    void shouldHaveUniqueKeysWithWellFormedPlaceholders() {
        Set<String> keys = new HashSet<>();
        for (MessageKey messageKey : MessageKey.values()) {
            String key = messageKey.getKey();
            if (!keys.add(key)) {
                fail("Found key '" + key + "' twice!");
            } else if (StringUtils.isBlank(key)) {
                fail("Key for message key '" + messageKey + "' is empty");
            }
            for (String tag : messageKey.getTags()) {
                assertThat("Tag '" + tag + "' corresponds to valid format for key '" + messageKey + "'",
                    tag, matchesPattern("^%[a-z_]+$"));
            }
        }
    }
}
