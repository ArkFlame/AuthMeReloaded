package fr.xephi.authme.datasource.converter;

import fr.xephi.authme.ConsoleLogger;
import fr.xephi.authme.security.crypts.HashedPassword;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helpers shared by the SQLite/HOCON-based plugin converters.
 */
final class ConverterUtils {

    private ConverterUtils() {
    }

    /**
     * Reconstructs the AuthMe hash from the password fields used by LibreLogin and NexAuth.
     *
     * @param hash the stored password hash
     * @param salt the stored salt
     * @param algo the source algorithm identifier
     * @param name the player name, used in log messages
     * @param argonAlgo the source identifier for Argon2 hashes
     * @param pluginName the source plugin name, used in log messages
     * @param logger logger for skipped accounts
     * @return the hash to import, or {@code null} if the account must be skipped
     */
    static HashedPassword buildHashedPassword(String hash, String salt, String algo, String name,
                                              String argonAlgo, String pluginName, ConsoleLogger logger) {
        if (hash == null || algo == null) {
            return null;
        }
        if ("BCrypt-2A".equals(algo)) {
            // The source splits the BCrypt string: hashed_password = "<cost>$<hashRemainder>",
            // salt = 22-char BCrypt base64 salt. Reconstruct: $2a$<cost>$<salt><hashRemainder>
            if (salt == null) {
                logger.warning("Null salt for BCrypt-2A account '" + name + "', skipping");
                return null;
            }
            int sep = hash.indexOf('$');
            if (sep < 0) {
                logger.warning("Malformed BCrypt-2A hash for player '" + name + "', skipping");
                return null;
            }
            return new HashedPassword("$2a$" + hash.substring(0, sep) + "$" + salt + hash.substring(sep + 1));
        }
        if (argonAlgo.equals(algo)) {
            String phc = decodeArgon2(hash);
            if (phc == null) {
                logger.warning("Could not decode Argon2-ID hash for player '" + name + "', skipping");
                return null;
            }
            return new HashedPassword(phc);
        }
        if ("SHA-256".equals(algo)) {
            // sha256(sha256(pw) + salt) — same as AuthMe SHA256: $SHA$<salt>$<hash>
            if (salt == null) {
                logger.warning("Null salt for SHA-256 account '" + name + "', skipping");
                return null;
            }
            return new HashedPassword("$SHA$" + salt + "$" + hash);
        }
        if ("SHA-512".equals(algo) || "LOGIT-SHA-256".equals(algo)) {
            // SHA-512 maps to DOUBLE_SHA512, LOGIT-SHA-256 maps to SALTEDSHA256.
            if (salt == null) {
                logger.warning("Null salt for " + algo + " account '" + name + "', skipping");
                return null;
            }
            return new HashedPassword(hash, salt);
        }
        logger.warning("Unknown " + pluginName + " algorithm '" + algo + "' for player '" + name + "', skipping");
        return null;
    }

    /**
     * Returns the Argon2 PHC string, decoding it from Base64 when necessary.
     *
     * @param value the stored Argon2 hash
     * @return the PHC string, or {@code null} if it cannot be decoded
     */
    static String decodeArgon2(String value) {
        if (value.startsWith("$argon2")) {
            return value;
        }
        try {
            String decoded = new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
            if (decoded.startsWith("$argon2")) {
                return decoded;
            }
        } catch (IllegalArgumentException ignored) {
            // fall through
        }
        return null;
    }

    /**
     * Reads a scalar value from a HOCON config file by scanning for the first line matching
     * {@code <key> = <value>} or {@code <key>: <value>} (with optional surrounding whitespace
     * and quotes). Sufficient for the flat scalar keys used by these plugins; not a full parser.
     *
     * @param file the config file to read
     * @param key the key to look up
     * @param defaultValue the value to return when the key is absent
     * @return the configured value or {@code defaultValue}
     */
    static String readHoconValue(File file, String key, String defaultValue) {
        Pattern pattern = Pattern.compile(
            "^\\s*" + Pattern.quote(key) + "\\s*[=:]\\s*\"?([^\",\\s}#]+)\"?");
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher m = pattern.matcher(line);
                if (m.find()) {
                    return m.group(1);
                }
            }
        } catch (IOException e) {
            // fall through to default
        }
        return defaultValue;
    }
}
