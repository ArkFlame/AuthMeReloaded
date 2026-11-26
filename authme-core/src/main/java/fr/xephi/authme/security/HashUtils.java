package fr.xephi.authme.security;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Hashing utilities (interface for common hashing algorithms).
 */
public final class HashUtils {

    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    private HashUtils() {
    }

    /**
     * Generate the SHA-1 digest of the given message.
     *
     * @param message The message to hash
     * @return The resulting SHA-1 digest
     */
    public static String sha1(String message) {
        return hash(message, getDigest(MessageDigestAlgorithm.SHA1));
    }

    /**
     * Generate the SHA-256 digest of the given message.
     *
     * @param message The message to hash
     * @return The resulting SHA-256 digest
     */
    public static String sha256(String message) {
        return hash(message, getDigest(MessageDigestAlgorithm.SHA256));
    }

    /**
     * Generate the SHA-512 digest of the given message.
     *
     * @param message The message to hash
     * @return The resulting SHA-512 digest
     */
    public static String sha512(String message) {
        return hash(message, getDigest(MessageDigestAlgorithm.SHA512));
    }

    /**
     * Generate the MD5 digest of the given message.
     *
     * @param message The message to hash
     * @return The resulting MD5 digest
     */
    public static String md5(String message) {
        return hash(message, getDigest(MessageDigestAlgorithm.MD5));
    }

    /**
     * Return a {@link MessageDigest} instance for the given algorithm.
     *
     * @param algorithm The desired algorithm
     * @return MessageDigest instance for the given algorithm
     */
    public static MessageDigest getDigest(MessageDigestAlgorithm algorithm) {
        try {
            return MessageDigest.getInstance(algorithm.getKey());
        } catch (NoSuchAlgorithmException e) {
            throw new UnsupportedOperationException("Your system seems not to support the hash algorithm '"
                + algorithm.getKey() + "'");
        }
    }

    /**
     * Return whether the given hash starts like a BCrypt hash. Checking with this method
     * beforehand prevents the BCryptHasher from throwing certain exceptions.
     *
     * @param hash The salt to verify
     * @return True if the salt is valid, false otherwise
     */
    public static boolean isValidBcryptHash(String hash) {
        return hash.length() == 60 && hash.startsWith("$2");
    }

    /**
     * Compute the HMAC-SHA256 of the given message using the given secret key,
     * and return the result as a lowercase hexadecimal string.
     *
     * @param secret the secret key
     * @param message the message to authenticate
     * @return the HMAC-SHA256 digest as a hex string
     */
    public static String hmacSha256(String secret, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return toHex(digest);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new UnsupportedOperationException("HmacSHA256 is not available on this system", e);
        }
    }

    /**
     * Checks whether the two strings are equal to each other in a time-constant manner.
     * This helps to avoid timing side channel attacks,
     * cf. <a href="https://github.com/AuthMe/AuthMeReloaded/issues/1561">issue #1561</a>.
     *
     * @param string1 first string
     * @param string2 second string
     * @return true if the strings are equal to each other, false otherwise
     */
    public static boolean isEqual(String string1, String string2) {
        return isEqual(string1.getBytes(StandardCharsets.UTF_8), string2.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Checks whether the two byte arrays are equal to each other in a time-constant manner.
     * This is the canonical comparison for digests and other secret material.
     *
     * @param bytes1 first byte array
     * @param bytes2 second byte array
     * @return true if the byte arrays are equal to each other, false otherwise
     */
    public static boolean isEqual(byte[] bytes1, byte[] bytes2) {
        return MessageDigest.isEqual(bytes1, bytes2);
    }

    /**
     * Returns the given bytes as a lowercase hexadecimal string (two characters per byte).
     *
     * @param bytes the bytes to convert
     * @return the hexadecimal representation of the bytes
     */
    public static String toHex(byte[] bytes) {
        char[] chars = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; ++i) {
            int value = bytes[i] & 0xFF;
            chars[i * 2] = HEX_CHARS[value >>> 4];
            chars[i * 2 + 1] = HEX_CHARS[value & 0x0F];
        }
        return new String(chars);
    }

    /**
     * Hash the message with the given algorithm and return the hash in its hexadecimal notation.
     *
     * @param message The message to hash
     * @param algorithm The algorithm to hash the message with
     * @return The digest in its hexadecimal representation
     */
    public static String hash(String message, MessageDigest algorithm) {
        algorithm.reset();
        algorithm.update(message.getBytes());
        byte[] digest = algorithm.digest();
        return toHex(digest);
    }
}
