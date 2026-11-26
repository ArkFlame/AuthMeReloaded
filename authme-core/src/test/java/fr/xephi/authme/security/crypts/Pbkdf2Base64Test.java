package fr.xephi.authme.security.crypts;
import org.junit.jupiter.api.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
/**
 * Test for {@link Pbkdf2Base64}.
 */
class Pbkdf2Base64Test extends AbstractEncryptionMethodTest {
    Pbkdf2Base64Test() {
        super(new Pbkdf2Base64(CryptTestSettings.withPbkdf2Rounds(4128)),
            "pbkdf2$4128$JddgMm9rNhbYNfEmf4pOKA==$9Q7QyaEDhqAj8KfF7FdPRq6f9uAWhMwioKvzAWMxSu8=",  // password
            "pbkdf2$4128$obLD1OX2p7jJ0OHyo7TF1g==$dyEnM1cYBRhXl0bDgxfQ3p7Jecj4VerT1Vsag5HJTmo=",  // PassWord1
            "pbkdf2$4128$3q2+78r+AQIDBAUGBwgJCg==$L1stW/OuuWuLfFiEqpOSumXwSJROYDUwSDfiU/7CkYA=",  // &^%te$t?Pw@_
            "pbkdf2$4128$AQIDBAUGBwgJCgsMDQ4PEA==$djo4l/sF2mo7hNBflk/JZNFURfNbYDT2KUjJ4n1K/8Y="); // âË_3(íù*
    }
    @Test
    void shouldMatchHashWithDifferentRoundNumber() {
        Pbkdf2Base64 pbkdf2Base64 = new Pbkdf2Base64(CryptTestSettings.withPbkdf2Rounds(4128));
        String hash = "pbkdf2$120000$JddgMm9rNhbYNfEmf4pOKA==$RcAMtgm/KnKFxfNOpg95tb7s5OzB2Fv4Wj1HOAI/TWY=";
        assertThat(pbkdf2Base64.comparePassword("azerty123", new HashedPassword(hash), ""), equalTo(true));
    }
}
