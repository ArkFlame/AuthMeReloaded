package fr.xephi.authme.util.message;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test for {@link MiniMessageConverter}.
 */
class MiniMessageConverterTest {

    @Test
    void convertsMiniMessageTagsToLegacyColors() {

        String result = MiniMessageConverter.convert("<red>Hello</red>", true);

        assertTrue(result.contains("\u00a7cHello"), "Expected a legacy red color code, got: " + result);
    }

    @Test
    void leavesPlainAndAmpersandTextUnchanged() {
        assertEquals("Hello, world!", MiniMessageConverter.convert("Hello, world!", true));
        assertEquals("&cColored &ltext", MiniMessageConverter.convert("&cColored &ltext", true));
        // Message placeholders must not be mistaken for MiniMessage tags.
        assertFalse(MiniMessageConverter.containsMiniMessageTags("/register <password> <confirmPassword>"));
    }

    @Test
    void disabledSettingLeavesTextUntouched() {
        String message = "<red>Hello</red> &cworld";

        assertEquals(message, MiniMessageConverter.convert(message, false));
    }
}
