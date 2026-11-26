package fr.xephi.authme.security.crypts;

import fr.xephi.authme.ConsoleLogger;
import fr.xephi.authme.output.ConsoleLoggerFactory;
import fr.xephi.authme.security.crypts.description.AsciiRestricted;

import java.nio.charset.StandardCharsets;
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
        return AbstractPbkdf2.hashPrefix("pbkdf2_sha256", DEFAULT_ITERATIONS, salt)
            + Base64.getEncoder().encodeToString(derived);
    }

    @Override
    public boolean comparePassword(String password, HashedPassword hashedPassword, String unusedName) {
        AbstractPbkdf2.HashParts parts = AbstractPbkdf2.parseHash(hashedPassword, logger, "Pbkdf2Django");
        if (parts == null) {
            return false;
        }
        byte[] expected = Base64.getDecoder().decode(parts.getHash());
        return AbstractPbkdf2.verifyKey(password, parts.getSalt().getBytes(StandardCharsets.US_ASCII),
            parts.getIterations(), expected, StandardCharsets.US_ASCII);
    }

    @Override
    public int getSaltLength() {
        return 12;
    }
}
