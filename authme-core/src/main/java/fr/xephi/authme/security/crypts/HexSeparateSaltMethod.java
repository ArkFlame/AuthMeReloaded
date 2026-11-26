package fr.xephi.authme.security.crypts;

import fr.xephi.authme.util.RandomStringUtils;

/**
 * Common supertype for encryption methods which store a random hexadecimal salt
 * separately from the hash.
 */
public abstract class HexSeparateSaltMethod extends SeparateSaltMethod {

    /**
     * @return the number of hexadecimal characters of the generated salt
     */
    public abstract int getSaltLength();

    @Override
    public String generateSalt() {
        return RandomStringUtils.generateHex(getSaltLength());
    }
}
