package fr.xephi.authme.security.crypts;

import java.security.SecureRandom;

/**
 * Shared helper for generating the random bytes used as a cryptographic salt.
 */
final class SaltBytes {

    private SaltBytes() {
    }

    /**
     * Generates {@code count} random bytes with a fresh secure random generator.
     *
     * @param count the number of bytes to generate
     * @return the random bytes
     */
    static byte[] random(int count) {
        return random(count, new SecureRandom());
    }

    /**
     * Generates {@code count} random bytes with the given secure random generator.
     *
     * @param count the number of bytes to generate
     * @param random the generator to draw from
     * @return the random bytes
     */
    static byte[] random(int count, SecureRandom random) {
        byte[] result = new byte[count];
        random.nextBytes(result);
        return result;
    }
}
