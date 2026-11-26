package fr.xephi.authme.datasource.converter;

import fr.xephi.authme.ConsoleLogger;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.output.ConsoleLoggerFactory;
import fr.xephi.authme.security.crypts.HashedPassword;
import fr.xephi.authme.util.UuidUtils;
import org.bukkit.configuration.file.YamlConfiguration;

import javax.inject.Inject;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Converts data from nLogin to AuthMe. Reads {@code plugins/nLogin/config.yml}:
 * {@code database.type: SQLite} uses {@code plugins/nLogin/nlogin.db}, any other value uses the
 * shared SQL pool. The account table comes from {@code database.table.account.table-name}
 * (default {@code nlogin}). Hashes are copied as-is (BCrypt, SHA-256, SHA-512, Argon2); set
 * AuthMe's {@code passwordHash} to match (default {@code BCRYPT}).
 */
public class NLoginConverter extends AbstractYamlPluginConverter {

    private static final String CONFIG_PATH = "plugins/nLogin/config.yml";
    private static final String SQLITE_DB_PATH = "plugins/nLogin/nlogin.db";
    private static final String DEFAULT_TABLE = "nlogin";

    private static final ConsoleLogger logger = ConsoleLoggerFactory.get(NLoginConverter.class);

    @Inject
    NLoginConverter(DataSource dataSource) {
        super(dataSource, CONFIG_PATH, SQLITE_DB_PATH, "nLogin", logger);
    }

    @Override
    protected Backend getBackend(YamlConfiguration config) {
        return "SQLite".equalsIgnoreCase(config.getString("database.type", "SQLite"))
            ? Backend.SQLITE
            : Backend.SQL;
    }

    @Override
    protected String getQuery(YamlConfiguration config) {
        String table = config.getString("database.table.account.table-name", DEFAULT_TABLE);
        return "SELECT last_name, password, last_ip, email, creation_date, last_seen, unique_id FROM " + table;
    }

    @Override
    protected String unsupportedMessage(YamlConfiguration config) {
        return "nLogin conversion: database type '" + config.getString("database.type", "SQLite")
            + "' is not supported.";
    }

    @Override
    protected String getName(ResultSet rs) throws SQLException {
        return ConverterUtils.toLowerCaseOrNull(rs.getString("last_name"));
    }

    @Override
    protected PlayerAuth buildAuth(ResultSet rs, String name) throws SQLException {
        String hash = rs.getString("password");
        if (!ConverterUtils.hasPassword(hash, name, logger)) {
            return null;
        }
        long creationDate = rs.getLong("creation_date");
        long lastLogin = rs.getLong("last_seen");
        return PlayerAuth.builder()
            .name(name)
            .realName(rs.getString("last_name"))
            .password(new HashedPassword(hash))
            .lastIp(rs.getString("last_ip"))
            .email(rs.getString("email"))
            .registrationDate(creationDate)
            .lastLogin(lastLogin > 0 ? lastLogin : null)
            .uuid(parseNLoginUuid(rs.getString("unique_id")))
            .build();
    }

    /**
     * Parses a UUID from a 32-char hex string (nLogin's format, no dashes) or a standard UUID string.
     */
    private static UUID parseNLoginUuid(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        if (value.length() == 32) {
            value = value.substring(0, 8) + "-"
                + value.substring(8, 12) + "-"
                + value.substring(12, 16) + "-"
                + value.substring(16, 20) + "-"
                + value.substring(20);
        }
        return UuidUtils.parseUuidSafely(value);
    }
}
