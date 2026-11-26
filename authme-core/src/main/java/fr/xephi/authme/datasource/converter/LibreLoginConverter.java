package fr.xephi.authme.datasource.converter;

import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.output.ConsoleLoggerFactory;

import javax.inject.Inject;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

/**
 * Converts data from LibreLogin to AuthMe.
 * <p>
 * The storage backend is determined by reading {@code plugins/LibreLogin/config.conf}
 * ({@code database.type}):
 * <ul>
 *   <li><b>librelogin-sqlite</b> (default) — reads the SQLite database at the path configured
 *       under {@code sqlite.path} (default: {@code plugins/LibreLogin/user-data.db}).</li>
 *   <li><b>librelogin-mysql / librelogin-postgresql</b> — uses AuthMe's existing connection
 *       pool; LibreLogin must share the same database as AuthMe.</li>
 * </ul>
 * <p>
 * <b>Algorithm mapping:</b>
 * <ul>
 *   <li>{@code BCrypt-2A} → configure AuthMe with {@code passwordHash: BCRYPT}</li>
 *   <li>{@code Argon2-ID} → configure AuthMe with {@code passwordHash: ARGON2ID}</li>
 *   <li>{@code SHA-256} → configure AuthMe with {@code passwordHash: SHA256}</li>
 *   <li>{@code SHA-512} → configure AuthMe with {@code passwordHash: DOUBLE_SHA512}</li>
 *   <li>{@code LOGIT-SHA-256} → configure AuthMe with {@code passwordHash: SALTEDSHA256}</li>
 * </ul>
 */
public class LibreLoginConverter extends AbstractLibrePremiumConverter {

    @Inject
    LibreLoginConverter(DataSource dataSource) {
        super(dataSource, ConsoleLoggerFactory.get(LibreLoginConverter.class),
            "plugins/LibreLogin/config.conf", "LibreLogin", "librelogin-",
            "plugins/LibreLogin/", "Argon2-ID");
    }

    @Override
    protected ImportDates readDates(ResultSet rs) throws SQLException {
        Timestamp joined = rs.getTimestamp("joined");
        Timestamp lastSeen = rs.getTimestamp("last_seen");
        return new ImportDates(joined != null ? joined.getTime() : 0L,
            lastSeen != null ? lastSeen.getTime() : null);
    }
}
