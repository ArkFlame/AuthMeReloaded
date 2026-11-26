package fr.xephi.authme.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.function.IntFunction;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Test for {@link RandomStringUtils}.
 */
class RandomStringUtilsTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("generators")
    void shouldGenerateRandomStrings(String name, IntFunction<String> generator, int[] lengths,
                                     Pattern badChars, String allowedCharacters) {
        for (int length : lengths) {
            String result = generator.apply(length);
            assertThat("Result '" + result + "' should have length " + length,
                result.length(), equalTo(length));
            assertThat("Result '" + result + "' should only have characters " + allowedCharacters,
                badChars.matcher(result).matches(), equalTo(false));
        }
    }

    private static Stream<Arguments> generators() {
        return Stream.of(
            Arguments.of("lowercase alphanumeric", (IntFunction<String>) RandomStringUtils::generate,
                new int[]{0, 1, 19, 142, 1872}, Pattern.compile(".*[^0-9a-z].*"), "a-z, 0-9"),
            Arguments.of("hexadecimal", (IntFunction<String>) RandomStringUtils::generateHex,
                new int[]{0, 1, 21, 160, 1784}, Pattern.compile(".*[^0-9a-f].*"), "a-f, 0-9"),
            Arguments.of("lower and upper case", (IntFunction<String>) RandomStringUtils::generateLowerUpper,
                new int[]{0, 1, 17, 143, 1808}, Pattern.compile(".*[^0-9a-zA-Z].*"), "a-z, A-Z, 0-9"),
            Arguments.of("numeric", (IntFunction<String>) RandomStringUtils::generateNum,
                new int[]{0, 1, 18, 147, 1833}, Pattern.compile(".*[^0-9].*"), "0-9"));
    }

    @Test
    void shouldThrowForInvalidLength() {
        assertThrows(IllegalArgumentException.class,
            () -> RandomStringUtils.generate(-3));
    }
}
