package fr.xephi.authme.security.crypts;
import org.junit.jupiter.api.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.startsWith;
/**
 * Test for {@link BCrypt}.
 */
class BCryptTest extends AbstractEncryptionMethodTest {
    BCryptTest() {
        super(new BCrypt(CryptTestSettings.withBcryptRounds(8)),
            "$2a$10$6iATmYgwJVc3YONhVcZFve3Cfb5GnwvKhJ20r.hMjmcNkIT9.Uh9K", // password
            "$2a$10$LOhUxhEcS0vgDPv/jkXvCurNb7LjP9xUlEolJGk.Uhgikqc6FtIOi", // PassWord1
            "$2a$10$j9da7SGiaakWhzIms9BtwemLUeIhSEphGUQ3XSlvYgpYsGnGCKRBa", // &^%te$t?Pw@_
            "$2a$10$mkmO3SNzQT/SA5fG/8P8PePz/DI/kKpIH8vd1Owf/fQfFu6F0QyWO"  // âË_3(íù*
        );
    }
    @Test
    void shouldGenerateWith2aPrefix() {
        BCrypt bCrypt = new BCrypt(CryptTestSettings.withBcryptRounds(8));
        HashedPassword result = bCrypt.computeHash("test", null);
        assertThat(result.getHash(), startsWith("$2a$08$"));
    }
}
