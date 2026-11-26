package fr.xephi.authme.util;

import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Test for {@link RandomStringUtils}.
 */
class RandomStringUtilsTest {

    @Test
    void shouldGenerateRandomStrings() {
        int[] lengths = {0, 1, 19, 142, 1872};
        Pattern badChars = Pattern.compile(".*[^0-9a-z].*");

        for (int length : lengths) {
            String result = RandomStringUtils.generate(length);
            assertThat("Result '" + result + "' should have length " + length,
                result.length(), equalTo(length));
            assertThat("Result '" + result + "' should only have characters a-z, 0-9",
                badChars.matcher(result).matches(), equalTo(false));
        }
    }

    @Test
    void shouldGenerateRandomHexString() {
        int[] lengths = {0, 1, 21, 160, 1784};
        Pattern badChars = Pattern.compile(".*[^0-9a-f].*");

        for (int length : lengths) {
            String result = RandomStringUtils.generateHex(length);
            assertThat("Result '" + result + "' should have length " + length,
                result.length(), equalTo(length));
            assertThat("Result '" + result + "' should only have characters a-f, 0-9",
                badChars.matcher(result).matches(), equalTo(false));
        }
    }

    @Test
    void shouldGenerateRandomLowerUpperString() {
        int[] lengths = {0, 1, 17, 143, 1808};
        Pattern badChars = Pattern.compile(".*[^0-9a-zA-Z].*");

        for (int length : lengths) {
            String result = RandomStringUtils.generateLowerUpper(length);
            assertThat("Result '" + result + "' should have length " + length,
                result.length(), equalTo(length));
            assertThat("Result '" + result + "' should only have characters a-z, A-Z, 0-9",
                badChars.matcher(result).matches(), equalTo(false));
        }
    }

    @Test
    void shouldGenerateRandomNumberString() {
        int[] lengths = {0, 1, 18, 147, 1833};
        Pattern badChars = Pattern.compile(".*[^0-9].*");

        for (int length : lengths) {
            String result = RandomStringUtils.generateNum(length);
            assertThat("Result '" + result + "' should have length " + length,
                result.length(), equalTo(length));
            assertThat("Result '" + result + "' should only have characters 0-9",
                badChars.matcher(result).matches(), equalTo(false));
        }
    }

    @Test
    void shouldThrowForInvalidLength() {
        assertThrows(IllegalArgumentException.class,
            () -> RandomStringUtils.generate(-3));
    }
}
