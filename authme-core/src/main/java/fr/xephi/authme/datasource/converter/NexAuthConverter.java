package fr.xephi.authme.datasource.converter;

import fr.xephi.authme.ConsoleLogger;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.output.ConsoleLoggerFactory;
import fr.xephi.authme.security.crypts.HashedPassword;
import fr.xephi.authme.util.UuidUtils;
import org.bukkit.command.CommandSender;

import javax.inject.Inject;
import java.io.File;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;

import static fr.xephi.authme.util.Utils.logAndSendMessage;

/**
 * Converts data from NexAuth to AuthMe.
 * <p>
 * The storage backend is determined by reading {@code plugins/NexAuth/config.conf}
 * ({@code database.type}):
 * <ul>
 *   <li><b>nexauth-sqlite</b> (default) — reads the SQLite database at the path configured
 *       under {@code database.properties.sqlite.path} (default: {@code user-data.db}).</li>
 *   <li><b>nexauth-mysql / nexauth-postgresql</b> — uses AuthMe's existing connection
 *       pool; NexAuth must share the same database as AuthMe.</li>
 * </ul>
 * <p>
 * <b>Algorithm mapping:</b>
 * <ul>
 *   <li>{@code BCrypt-2A} → configure AuthMe with {@code passwordHash: BCRYPT}</li>
 *   <li>{@code Argon-2ID} → configure AuthMe with {@code passwordHash: ARGON2}</li>
 *   <li>{@code SHA-256} → configure AuthMe with {@code passwordHash: SHA256}</li>
 *   <li>{@code SHA-512} → configure AuthMe with {@code passwordHash: DOUBLE_SHA512}</li>
 *   <li>{@code LOGIT-SHA-256} → configure AuthMe with {@code passwordHash: SALTEDSHA256}</li>
 * </ul>
 */
public class NexAuthConverter implements Converter {

    private static final String CONFIG_PATH = "plugins/NexAuth/config.conf";
    private static final String DEFAULT_SQLITE_FILENAME = "user-data.db";
    private static final String TABLE = "librepremium_data";
    private static final String QUERY = "SELECT last_nickname, hashed_password, salt, algo, "
        + "ip, email, joined, last_seen, uuid, premium_uuid, secret FROM " + TABLE;

    private static final ConsoleLogger logger = ConsoleLoggerFactory.get(NexAuthConverter.class);
    private final DataSource dataSource;

    @Inject
    NexAuthConverter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void execute(CommandSender sender) {
        File configFile = new File(CONFIG_PATH);
        if (!configFile.exists()) {
            logAndSendMessage(sender, "NexAuth conversion failed: config not found at " + CONFIG_PATH);
            return;
        }

        String dbType = ConverterUtils.readHoconValue(configFile, "type", "nexauth-sqlite");

        switch (dbType) {
            case "nexauth-sqlite": {
                String sqliteFilename = ConverterUtils.readHoconValue(configFile, "path", DEFAULT_SQLITE_FILENAME);
                new SqliteImport(dataSource, new File("plugins/NexAuth/" + sqliteFilename)).execute(sender);
                break;
            }
            case "nexauth-mysql":
            case "nexauth-postgresql":
                new SqlImport(dataSource).execute(sender);
                break;
            default:
                logAndSendMessage(sender,
                    "NexAuth conversion: database type '" + dbType + "' is not supported.");
        }
    }

    private static void importRows(ResultSet rs, DataSource dataSource, CommandSender sender) throws SQLException {
        long imported = 0;
        long skipped = 0;
        while (rs.next()) {
            String realName = rs.getString("last_nickname");
            if (realName == null || realName.isEmpty()) {
                continue;
            }
            String name = realName.toLowerCase(Locale.ROOT);

            if (dataSource.isAuthAvailable(name)) {
                ++skipped;
                continue;
            }

            HashedPassword password = ConverterUtils.buildHashedPassword(
                rs.getString("hashed_password"),
                rs.getString("salt"),
                rs.getString("algo"),
                name, "Argon-2ID", "NexAuth", logger);
            if (password == null) {
                continue;
            }

            long joined = rs.getLong("joined");
            long lastSeen = rs.getLong("last_seen");

            PlayerAuth auth = PlayerAuth.builder()
                .name(name)
                .realName(realName)
                .password(password)
                .lastIp(rs.getString("ip"))
                .email(rs.getString("email"))
                .registrationDate(joined > 0 ? joined : 0L)
                .lastLogin(lastSeen > 0 ? lastSeen : null)
                .totpKey(rs.getString("secret"))
                .uuid(UuidUtils.parseUuidSafely(rs.getString("uuid")))
                .premiumUuid(UuidUtils.parseUuidSafely(rs.getString("premium_uuid")))
                .build();

            dataSource.saveAuth(auth);
            ++imported;
        }

        logAndSendMessage(sender, "NexAuth conversion: " + imported + " account(s) imported, "
            + skipped + " skipped (already exist)");
    }

    private static final class SqliteImport extends AbstractSqlitePluginConverter {

        private final File dbFile;

        SqliteImport(DataSource dataSource, File dbFile) {
            super(dataSource);
            this.dbFile = dbFile;
        }

        @Override
        public void execute(CommandSender sender) {
            if (!dbFile.exists()) {
                logAndSendMessage(sender,
                    "NexAuth conversion failed: SQLite database not found at " + dbFile.getPath());
                return;
            }

            queryAndImport(dbFile, sender, QUERY, "NexAuth", logger,
                rs -> importRows(rs, getDataSource(), sender));
        }
    }

    private static final class SqlImport extends AbstractSqlPluginConverter {

        SqlImport(DataSource dataSource) {
            super(dataSource);
        }

        @Override
        public void execute(CommandSender sender) {
            queryAndImport(sender, QUERY, "NexAuth", logger,
                rs -> importRows(rs, getDataSource(), sender));
        }
    }
}
