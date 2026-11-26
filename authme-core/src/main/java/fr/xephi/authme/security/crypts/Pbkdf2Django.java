package fr.xephi.authme.security.crypts;

import com.google.common.primitives.Ints;
import fr.xephi.authme.ConsoleLogger;
import fr.xephi.authme.output.ConsoleLoggerFactory;
import fr.xephi.authme.security.crypts.description.AsciiRestricted;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

@AsciiRestricted
public class Pbkdf2Django extends HexSaltedMethod {

    private static final int DEFAULT_ITERATIONS = 24000;
    private static final int HASH_BYTES = 32;
    private final ConsoleLogger logger = ConsoleLoggerFactory.get(Pbkdf2Django.class);

    @Override
    public String computeHash(String password, String salt, String name) {
        byte[] derived = AbstractPbkdf2.deriveKey(password, salt.getBytes(StandardCharsets.US_ASCII),
            DEFAULT_ITERATIONS, HASH_BYTES, StandardCharsets.US_ASCII);
        return "pbkdf2_sha256$" + DEFAULT_ITERATIONS + "$" + salt + "$"
            + Base64.getEncoder().encodeToString(derived);
    }

    @Override
    public boolean comparePassword(String password, HashedPassword hashedPassword, String unusedName) {
        String[] line = hashedPassword.getHash().split("\\$");
        if (line.length != 4) {
            return false;
        }
        Integer iterations = Ints.tryParse(line[1]);
        if (iterations == null) {
            logger.warning("Cannot read number of rounds for Pbkdf2Django: '" + line[1] + "'");
            return false;
        }
        String salt = line[2];
        byte[] expected = Base64.getDecoder().decode(line[3]);
        byte[] computed = AbstractPbkdf2.deriveKey(password, salt.getBytes(StandardCharsets.US_ASCII),
            iterations, HASH_BYTES, StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(computed, expected);
    }

    @Override
    public int getSaltLength() {
        return 12;
    }
}
