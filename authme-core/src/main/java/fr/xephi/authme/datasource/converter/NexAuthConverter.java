package fr.xephi.authme.datasource.converter;

import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.output.ConsoleLoggerFactory;

import javax.inject.Inject;
import java.sql.ResultSet;
import java.sql.SQLException;

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
public class NexAuthConverter extends AbstractLibrePremiumConverter {

    @Inject
    NexAuthConverter(DataSource dataSource) {
        super(dataSource, ConsoleLoggerFactory.get(NexAuthConverter.class),
            "plugins/NexAuth/config.conf", "NexAuth", "nexauth-",
            "plugins/NexAuth/", "Argon-2ID");
    }

    @Override
    protected ImportDates readDates(ResultSet rs) throws SQLException {
        long joined = rs.getLong("joined");
        long lastSeen = rs.getLong("last_seen");
        return new ImportDates(joined > 0 ? joined : 0L, lastSeen > 0 ? lastSeen : null);
    }
}
