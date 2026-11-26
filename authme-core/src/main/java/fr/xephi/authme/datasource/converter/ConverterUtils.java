package fr.xephi.authme.datasource.converter;

import fr.xephi.authme.ConsoleLogger;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.security.crypts.HashedPassword;
import org.bukkit.command.CommandSender;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static fr.xephi.authme.util.Utils.logAndSendMessage;

/**
 * Helpers shared by the SQLite/HOCON-based plugin converters.
 */
final class ConverterUtils {

    private ConverterUtils() {
    }

    /**
     * Checks that a plugin's config file is present before a conversion is started.
     *
     * @param sender the sender to report the problem to
     * @param configFile the config file to check
     * @param pluginName name of the source plugin, used in the error message
     * @return true when the file exists and the conversion may proceed
     */
    static boolean hasConfigFile(CommandSender sender, File configFile, String pluginName) {
        if (!configFile.exists()) {
            logAndSendMessage(sender, pluginName + " conversion failed: config not found at " + configFile.getPath());
            return false;
        }
        return true;
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
        if ("SHA-256".equals(algo) || "SHA-512".equals(algo) || "LOGIT-SHA-256".equals(algo)) {
            if (salt == null) {
                logger.warning("Null salt for " + algo + " account '" + name + "', skipping");
                return null;
            }
            // SHA-256 maps to $SHA$<salt>$<hash>, SHA-512 to DOUBLE_SHA512, LOGIT-SHA-256 to SALTEDSHA256.
            return "SHA-256".equals(algo)
                ? new HashedPassword("$SHA$" + salt + "$" + hash)
                : new HashedPassword(hash, salt);
        }
        logger.warning("Unknown " + pluginName + " algorithm '" + algo + "' for player '" + name + "', skipping");
        return null;
    }

    /**
     * Verifies that a source account has a usable password hash, warning once when it does not.
     *
     * @param hash the stored hash
     * @param name the lowercased player name, used in the warning
     * @param logger logger for the warning
     * @return true when the hash may be imported
     */
    static boolean hasPassword(String hash, String name, ConsoleLogger logger) {
        if (hash == null || hash.isEmpty()) {
            logger.warning("No password for player '" + name + "', skipping");
            return false;
        }
        return true;
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
            return decoded.startsWith("$argon2") ? decoded : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
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

    /**
     * @return the given name in lowercase, or {@code null} if it is null or empty
     */
    static String toLowerCaseOrNull(String name) {
        return name == null || name.isEmpty() ? null : name.toLowerCase(Locale.ROOT);
    }

    /**
     * Shared account-import loop. Skips rows without a usable name, accounts that are already
     * present in AuthMe and accounts whose source data cannot be converted.
     *
     * @param rs the query results
     * @param dataSource the destination data source
     * @param sender the sender to report the outcome to
     * @param pluginName name of the source plugin, used in the summary
     * @param nameReader extracts the lowercase account name from a row
     * @param authBuilder turns a row into an account, or returns null to skip it
     * @throws SQLException on a database error
     */
    static void importRows(ResultSet rs, DataSource dataSource, CommandSender sender, String pluginName,
                           RowNameReader nameReader, RowAuthBuilder authBuilder) throws SQLException {
        long imported = 0;
        long skipped = 0;
        while (rs.next()) {
            String name = nameReader.read(rs);
            if (name == null || name.isEmpty()) {
                continue;
            }
            if (dataSource.isAuthAvailable(name)) {
                ++skipped;
                continue;
            }
            PlayerAuth auth = authBuilder.build(rs, name);
            if (auth == null) {
                continue;
            }
            dataSource.saveAuth(auth);
            ++imported;
        }
        logImportResult(sender, pluginName, imported, skipped);
    }

    /**
     * Reports how many accounts a conversion imported and how many it skipped.
     */
    static void logImportResult(CommandSender sender, String pluginName, long imported, long skipped) {
        logAndSendMessage(sender, pluginName + " conversion: " + imported + " account(s) imported, "
            + skipped + " skipped (already exist)");
    }

    /** Reads the account name of the current row of a converter query. */
    @FunctionalInterface
    interface RowNameReader {
        String read(ResultSet rs) throws SQLException;
    }

    /** Builds the AuthMe account for the current row, or returns null to skip the row. */
    @FunctionalInterface
    interface RowAuthBuilder {
        PlayerAuth build(ResultSet rs, String name) throws SQLException;
    }

    /** Opens the JDBC connection used by a converter query. */
    @FunctionalInterface
    interface SqlConnectionSupplier {
        Connection get() throws SQLException;
    }

    /**
     * Runs a query and passes its rows to the importer, reporting SQL errors to the sender and log.
     */
    static void runQuery(SqlConnectionSupplier connectionSupplier, CommandSender sender, String query,
                         String pluginName, ConsoleLogger logger, ResultSetImporter importer) {
        try (Connection conn = connectionSupplier.get();
             PreparedStatement ps = conn.prepareStatement(query);
             ResultSet rs = ps.executeQuery()) {
            importer.importRows(rs);
        } catch (SQLException e) {
            logAndSendMessage(sender, pluginName + " conversion failed: " + e.getMessage());
            logger.logException(pluginName + " conversion error:", e);
        }
    }
}
