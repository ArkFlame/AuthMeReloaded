package fr.xephi.authme.security.crypts;

import fr.xephi.authme.ConsoleLogger;
import fr.xephi.authme.output.ConsoleLoggerFactory;
import fr.xephi.authme.security.crypts.description.Recommendation;
import fr.xephi.authme.security.crypts.description.Usage;
import fr.xephi.authme.settings.Settings;

import javax.inject.Inject;
import java.util.Base64;

/**
 * PBKDF2-HmacSHA256 with Base64-encoded salt and hash.
 * Format: {@code pbkdf2$<iterations>$<salt_base64>$<hash_base64>}
 */
@Recommendation(Usage.RECOMMENDED)
public class Pbkdf2Base64 extends AbstractPbkdf2 {

    private static final int DEFAULT_ROUNDS = 120_000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BYTES = 32;

    private final ConsoleLogger logger = ConsoleLoggerFactory.get(Pbkdf2Base64.class);

    @Inject
    Pbkdf2Base64(Settings settings) {
        super(settings, DEFAULT_ROUNDS);
    }

    @Override
    public String computeHash(String password, String salt, String name) {
        byte[] saltBytes = Base64.getDecoder().decode(salt);
        byte[] hash = deriveKey(password, saltBytes, numberOfRounds, HASH_BYTES);
        return hashPrefix("pbkdf2", numberOfRounds, salt) + Base64.getEncoder().encodeToString(hash);
    }

    @Override
    public boolean comparePassword(String password, HashedPassword hashedPassword, String unusedName) {
        HashParts parts = parseHash(hashedPassword, logger, "Pbkdf2Base64");
        if (parts == null) {
            return false;
        }
        byte[] saltBytes = Base64.getDecoder().decode(parts.salt);
        byte[] expectedKey = Base64.getDecoder().decode(parts.hash);
        return verifyKey(password, saltBytes, parts.iterations, expectedKey);
    }

    @Override
    public String generateSalt() {
        return Base64.getEncoder().encodeToString(SaltBytes.random(SALT_BYTES));
    }

    @Override
    public int getSaltLength() {
        return SALT_BYTES;
    }
}
