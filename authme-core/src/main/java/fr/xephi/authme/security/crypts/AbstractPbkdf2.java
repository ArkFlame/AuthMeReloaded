package fr.xephi.authme.security.crypts;

import com.google.common.primitives.Ints;
import fr.xephi.authme.ConsoleLogger;
import fr.xephi.authme.settings.Settings;
import fr.xephi.authme.settings.properties.SecuritySettings;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.generators.PKCS5S2ParametersGenerator;
import org.bouncycastle.crypto.params.KeyParameter;

import java.nio.charset.Charset;

import static fr.xephi.authme.security.HashUtils.isEqual;

public abstract class AbstractPbkdf2 extends HexSaltedMethod {

    protected final int numberOfRounds;

    protected AbstractPbkdf2(Settings settings, int defaultRounds) {
        int configured = settings.getProperty(SecuritySettings.PBKDF2_NUMBER_OF_ROUNDS);
        this.numberOfRounds = configured > 0 ? configured : defaultRounds;
    }

    /**
     * The four parts of a {@code scheme$iterations$salt$hash} line.
     */
    static final class HashParts {
        private final int iterations;
        private final String salt;
        private final String hash;

        HashParts(int iterations, String salt, String hash) {
            this.iterations = iterations;
            this.salt = salt;
            this.hash = hash;
        }

        protected int getIterations() {
            return iterations;
        }

        protected String getSalt() {
            return salt;
        }

        protected String getHash() {
            return hash;
        }
    }

    /**
     * Splits and validates a PBKDF2-style hash line shared by all PBKDF2 variants.
     *
     * @param hashedPassword the stored hash
     * @param logger logger used to report an unreadable iteration count
     * @param methodName name inserted into the warning message
     * @return the parsed parts, or null if the format is invalid
     */
    protected static HashParts parseHash(HashedPassword hashedPassword, ConsoleLogger logger, String methodName) {
        String[] parts = hashedPassword.getHash().split("\\$");
        if (parts.length != 4) {
            return null;
        }
        Integer iterations = Ints.tryParse(parts[1]);
        if (iterations == null) {
            logger.warning("Cannot read number of rounds for " + methodName + ": '" + parts[1] + "'");
            return null;
        }
        return new HashParts(iterations, parts[2], parts[3]);
    }

    /**
     * Builds the common {@code scheme$iterations$salt$} prefix of a PBKDF2 hash.
     *
     * @param scheme the hash scheme identifier
     * @param iterations the number of PBKDF2 iterations
     * @param salt the encoded salt
     * @return the prefix to append the encoded hash to
     */
    protected static String hashPrefix(String scheme, int iterations, String salt) {
        return scheme + "$" + iterations + "$" + salt + "$";
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
