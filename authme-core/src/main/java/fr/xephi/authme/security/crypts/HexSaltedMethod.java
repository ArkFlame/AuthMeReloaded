package fr.xephi.authme.security.crypts;

import fr.xephi.authme.security.crypts.description.HasSalt;
import fr.xephi.authme.security.crypts.description.Recommendation;
import fr.xephi.authme.security.crypts.description.SaltType;
import fr.xephi.authme.security.crypts.description.Usage;
import fr.xephi.authme.util.RandomStringUtils;

import static fr.xephi.authme.security.HashUtils.isEqual;

/**
 * Common type for encryption methods which use a random String of hexadecimal characters
 * and store the salt with the hash itself.
 */
@Recommendation(Usage.ACCEPTABLE)
@HasSalt(SaltType.TEXT) // See getSaltLength() for length
public abstract class HexSaltedMethod implements EncryptionMethod {

    public abstract int getSaltLength();

    @Override
    public abstract String computeHash(String password, String salt, String name);

    @Override
    public HashedPassword computeHash(String password, String name) {
        String salt = generateSalt();
        return new HashedPassword(computeHash(password, salt, null));
    }

    @Override
    public abstract boolean comparePassword(String password, HashedPassword hashedPassword, String name);

    @Override
    public String generateSalt() {
        return RandomStringUtils.generateHex(getSaltLength());
    }

    @Override
    public boolean hasSeparateSalt() {
        return false;
    }

    /**
     * Validates a stored hash of the form {@code $prefix$salt$hash}.
     *
     * @param password the clear-text password to verify
     * @param hashedPassword the stored hash
     * @param name the player name
     * @return true if the password matches the stored hash, false otherwise
     */
    protected boolean comparePrefixedHash(String password, HashedPassword hashedPassword, String name) {
        String hash = hashedPassword.getHash();
        String[] line = hash.split("\\$");
        return line.length == 4 && isEqual(hash, computeHash(password, line[2], name));
    }
}
