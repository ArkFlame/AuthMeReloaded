package fr.xephi.authme.security.crypts;

import fr.xephi.authme.settings.Settings;
import fr.xephi.authme.settings.properties.SecuritySettings;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.generators.PKCS5S2ParametersGenerator;
import org.bouncycastle.crypto.params.KeyParameter;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static fr.xephi.authme.security.HashUtils.isEqual;

public abstract class AbstractPbkdf2 extends HexSaltedMethod {

    protected final int numberOfRounds;

    protected AbstractPbkdf2(Settings settings, int defaultRounds) {
        int configured = settings.getProperty(SecuritySettings.PBKDF2_NUMBER_OF_ROUNDS);
        this.numberOfRounds = configured > 0 ? configured : defaultRounds;
    }

    /**
     * Derives a PBKDF2-HmacSHA256 key from the given password and salt.
     *
     * @param password the clear-text password
     * @param saltBytes the salt bytes
     * @param iterations the number of PBKDF2 iterations
     * @param keyLength the desired key length in bytes
     * @param charset the charset used to convert the password into bytes
     * @return the derived key
     */
    protected static byte[] deriveKey(String password, byte[] saltBytes, int iterations, int keyLength,
                                      Charset charset) {
        PKCS5S2ParametersGenerator gen = new PKCS5S2ParametersGenerator(new SHA256Digest());
        gen.init(password.getBytes(charset), saltBytes, iterations);
        return ((KeyParameter) gen.generateDerivedMacParameters(keyLength * 8)).getKey();
    }

    /**
     * Derives a key with the password encoded as UTF-8.
     *
     * @param password the clear-text password
     * @param saltBytes the salt bytes
     * @param iterations the number of PBKDF2 iterations
     * @param keyLength the desired key length in bytes
     * @return the derived key
     */
    protected byte[] deriveKey(String password, byte[] saltBytes, int iterations, int keyLength) {
        return deriveKey(password, saltBytes, iterations, keyLength, StandardCharsets.UTF_8);
    }

    /**
     * Verifies a key with the password encoded as UTF-8.
     *
     * @param password the clear-text password
     * @param saltBytes the salt bytes
     * @param iterations the number of PBKDF2 iterations
     * @param expectedKey the expected derived key
     * @return true if the derived key matches the expected key
     */
    protected boolean verifyKey(String password, byte[] saltBytes, int iterations, byte[] expectedKey) {
        return verifyKey(password, saltBytes, iterations, expectedKey, StandardCharsets.UTF_8);
    }

    /**
     * Verifies a derived key, encoding the password with the given charset.
     *
     * @param password the clear-text password
     * @param saltBytes the salt bytes
     * @param iterations the number of PBKDF2 iterations
     * @param expectedKey the expected derived key
     * @param charset the charset used to convert the password into bytes
     * @return true if the derived key matches the expected key
     */
    protected static boolean verifyKey(String password, byte[] saltBytes, int iterations, byte[] expectedKey,
                                       Charset charset) {
        byte[] computed = deriveKey(password, saltBytes, iterations, expectedKey.length, charset);
        return isEqual(computed, expectedKey);
    }
}
