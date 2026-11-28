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
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static fr.xephi.authme.util.Utils.logAndSendMessage;

/**
 * Helpers shared by the SQLite/HOCON-based plugin converters.
 */
final class ConverterUtils {

    private static final int MAX_CACHED_PATTERNS = 16;
    private static final ConcurrentHashMap<String, Pattern> HOCON_PATTERNS = new ConcurrentHashMap<>();

    private ConverterUtils() {
    }

    /** Checks that a plugin's config file exists before starting a conversion. */
    static boolean hasConfigFile(CommandSender sender, File configFile, String pluginName) {
        if (!configFile.exists()) {
            logAndSendMessage(sender, pluginName + " conversion failed: config not found at " + configFile.getPath());
            return false;
        }
        return true;
    }

    /**
     * Reconstructs the AuthMe hash from the password fields used by LibreLogin and NexAuth,
     * or returns null if the account must be skipped.
     */
    static HashedPassword buildHashedPassword(String hash, String salt, String algo, String name,
                                              String argonAlgo, String pluginName, ConsoleLogger logger) {
        if (hash == null || algo == null) {
            return null;
        }
        if ("BCrypt-2A".equals(algo)) {
            // hashed_password = "<cost>$<hashRemainder>", salt = 22-char BCrypt salt.
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
            String phc = hash;
            if (!phc.startsWith("$argon2")) {
                try {
                    phc = new String(Base64.getDecoder().decode(phc), StandardCharsets.UTF_8);
                } catch (IllegalArgumentException ignored) {
                    phc = null;
                }
            }
            if (phc == null || !phc.startsWith("$argon2")) {
                logger.warning("Could not decode Argon2-ID hash for player '" + name + "', skipping");
                return null;
            }
            return new HashedPassword(phc);
        }
        boolean sha256 = "SHA-256".equals(algo);
        if (sha256 || "SHA-512".equals(algo) || "LOGIT-SHA-256".equals(algo)) {
            if (salt == null) {
                logger.warning("Null salt for " + algo + " account '" + name + "', skipping");
                return null;
            }
            // SHA-256 maps to $SHA$<salt>$<hash>, SHA-512 to DOUBLE_SHA512, LOGIT-SHA-256 to SALTEDSHA256.
            return sha256 ? new HashedPassword("$SHA$" + salt + "$" + hash) : new HashedPassword(hash, salt);
        }
        logger.warning("Unknown " + pluginName + " algorithm '" + algo + "' for player '" + name + "', skipping");
        return null;
    }

    /** Returns false, with a warning, when the player has no usable password hash. */
    static boolean hasPassword(String hash, String name, ConsoleLogger logger) {
        if (hash == null || hash.isEmpty()) {
            logger.warning("No password for player '" + name + "', skipping");
            return false;
        }
        return true;
    }

    /**
     * Reads the first scalar {@code key = value} or {@code key: value} line from a HOCON file,
     * returning the default when the key is absent or the file cannot be read.
     */
    static String readHoconValue(File file, String key, String defaultValue) {
        if (HOCON_PATTERNS.size() >= MAX_CACHED_PATTERNS) {
            HOCON_PATTERNS.clear();
        }
        Pattern pattern = HOCON_PATTERNS.computeIfAbsent(key, k -> Pattern.compile(
            "^\\s*" + Pattern.quote(k) + "\\s*[=:]\\s*\"?([^\",\\s}#]+)\"?"));
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
            // Use the default value when the file cannot be read.
        }
        return defaultValue;
    }

    /** Returns the name in lowercase, or null when it is null or empty. */
    static String toLowerCaseOrNull(String name) {
        return name == null || name.isEmpty() ? null : name.toLowerCase(Locale.ROOT);
    }

    /**
     * Imports all rows, skipping unnamed accounts, accounts already in AuthMe and rows that
     * cannot be converted, then reports the outcome.
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
            if (dataSource.saveAuth(auth)) {
                ++imported;
            } else {
                logAndSendMessage(sender, pluginName + " conversion: could not save account '" + name + "'");
            }
        }
        logImportResult(sender, pluginName, imported, skipped);
    }

    /** Reports how many accounts a conversion imported and how many it skipped. */
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

    /** Runs a query, passing its rows to the importer and reporting SQL errors. */
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
