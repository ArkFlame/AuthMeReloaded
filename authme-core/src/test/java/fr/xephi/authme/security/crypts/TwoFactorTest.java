package fr.xephi.authme.security.crypts;

import fr.xephi.authme.TestHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * Test for {@link TwoFactor}.
 */
class TwoFactorTest {

    @BeforeAll
    static void initLogger() {
        TestHelper.setupLogger();
    }

    @Test
    void shouldGenerateBarcodeUrl() {
        String user = "tester";
        String host = "192.168.0.4";
        String secret = "3AK6Y4KWGRLJMEQW";

        String url = TwoFactor.getQrBarcodeUrl(user, host, secret);

        String expected = "https://www.google.com/chart?chs=130x130&chld=M%7C0&cht=qr"
            + "&chl=otpauth://totp/tester@192.168.0.4%3Fsecret%3D3AK6Y4KWGRLJMEQW";
        assertThat(url, equalTo(expected));
    }

    @ParameterizedTest(name = "reject hash {0} with input {1}")
    @MethodSource("invalidCredentials")
    void shouldRejectInvalidCredentials(String givenHash, String inputPassword) {
        HashedPassword password = new HashedPassword(givenHash);
        TwoFactor twoFactor = new TwoFactor();

        boolean result = twoFactor.comparePassword(inputPassword, password, "name");

        assertThat(result, equalTo(false));
    }

    static Stream<Arguments> invalidCredentials() {
        return Stream.of(
            Arguments.of("!@&#@!(*&@", "12345"),
            Arguments.of("3AK6Y4KWGRLJMEQW", "notA_number!"));
    }
}
