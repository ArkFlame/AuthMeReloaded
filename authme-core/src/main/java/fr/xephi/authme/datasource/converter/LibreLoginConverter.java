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
 * The storage backend is read from {@code plugins/LibreLogin/config.conf} ({@code database.type}):
 * {@code librelogin-sqlite} (default) reads the SQLite database configured under {@code sqlite.path}
 * (default {@code plugins/LibreLogin/user-data.db}); {@code librelogin-mysql} and
 * {@code librelogin-postgresql} use AuthMe's connection pool, so LibreLogin must share AuthMe's database.
 * <p>
 * Algorithm mapping: {@code BCrypt-2A} → {@code BCRYPT}, {@code Argon2-ID} → {@code ARGON2ID},
 * {@code SHA-256} → {@code SHA256}, {@code SHA-512} → {@code DOUBLE_SHA512},
 * {@code LOGIT-SHA-256} → {@code SALTEDSHA256}.
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
