package fr.xephi.authme.security.crypts;

import fr.xephi.authme.ConsoleLogger;
import fr.xephi.authme.output.ConsoleLoggerFactory;
import fr.xephi.authme.security.crypts.description.Recommendation;
import fr.xephi.authme.security.crypts.description.Usage;
import fr.xephi.authme.settings.Settings;
import org.bouncycastle.util.encoders.Hex;

import javax.inject.Inject;

@Recommendation(Usage.RECOMMENDED)
public class Pbkdf2 extends AbstractPbkdf2 {

    private static final int DEFAULT_ROUNDS = 10_000;
    private final ConsoleLogger logger = ConsoleLoggerFactory.get(Pbkdf2.class);

    @Inject
    Pbkdf2(Settings settings) {
        super(settings, DEFAULT_ROUNDS);
    }

    @Override
    public String computeHash(String password, String salt, String name) {
        return hashPrefix("pbkdf2_sha256", numberOfRounds, salt)
            + Hex.toHexString(deriveKey(password, salt.getBytes(), numberOfRounds, 64)).toUpperCase();
    }

    @Override
    public boolean comparePassword(String password, HashedPassword hashedPassword, String unusedName) {
        HashParts parts = parseHash(hashedPassword, logger, "Pbkdf2");
        if (parts == null) {
            return false;
        }
        return verifyKey(password, parts.salt.getBytes(), parts.iterations, Hex.decode(parts.hash));
    }

    @Override
    public int getSaltLength() {
        return 16;
    }

}
