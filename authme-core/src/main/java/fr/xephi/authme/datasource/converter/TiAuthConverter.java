/*
 * Ported from HaHaWTH/AuthMeReReloaded (AGPL-3.0), file
 * src/main/java/fr/xephi/authme/datasource/converter/TiAuthConverter.java.
 * Copyright (c) HaHaWTH contributors. Original licence retained; not relicensed.
 */

package fr.xephi.authme.datasource.converter;

import fr.xephi.authme.ConsoleLogger;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.output.ConsoleLoggerFactory;
import fr.xephi.authme.security.crypts.HashedPassword;
import org.bukkit.configuration.file.YamlConfiguration;

import javax.inject.Inject;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;

/**
 * Converts data from tiAuth to AuthMe. Reads {@code plugins/tiAuth/config.yml}:
 * {@code database.type: SQLITE} uses {@code plugins/tiAuth/auth.db},
 * {@code MYSQL}/{@code POSTGRESQL} use the shared pool; {@code H2} is unsupported.
 * BCrypt ({@code $2a$...}) and {@code $SHA$<salt>$<hash>} map to AuthMe's {@code BCRYPT}/{@code SHA256};
 * set {@code passwordHash} in AuthMe's {@code config.yml} accordingly.
 */
public class TiAuthConverter extends AbstractYamlPluginConverter {

    private static final String CONFIG_PATH = "plugins/tiAuth/config.yml";
    private static final String SQLITE_DB_PATH = "plugins/tiAuth/auth.db";
    private static final String QUERY =
        "SELECT username, realName, password, lastIp, lastLogin, regDate FROM auth_users";

    private static final ConsoleLogger logger = ConsoleLoggerFactory.get(TiAuthConverter.class);

    @Inject
    TiAuthConverter(DataSource dataSource) {
        super(dataSource, CONFIG_PATH, SQLITE_DB_PATH, "tiAuth", logger);
    }

    @Override
    protected Backend getBackend(YamlConfiguration config) {
        switch (readDbType(config)) {
            case "SQLITE":
                return Backend.SQLITE;
            case "MYSQL":
            case "POSTGRESQL":
                return Backend.SQL;
            default:
                return Backend.UNSUPPORTED;
        }
    }

    @Override
    protected String getQuery(YamlConfiguration config) {
        return QUERY;
    }

    @Override
    protected String unsupportedMessage(YamlConfiguration config) {
        return "tiAuth conversion: database type '" + readDbType(config) + "' is not supported. "
            + "Reconfigure tiAuth to use SQLite or MySQL/PostgreSQL, migrate the data, "
            + "then re-run the conversion.";
    }

    private static String readDbType(YamlConfiguration config) {
        return config.getString("database.type", "H2").toUpperCase(Locale.ROOT);
    }

    @Override
    protected String getName(ResultSet rs) throws SQLException {
        String realName = rs.getString("realName");
        return realName == null || realName.isEmpty() ? null : realName.toLowerCase(Locale.ROOT);
    }

    @Override
    protected PlayerAuth buildAuth(ResultSet rs, String name) throws SQLException {
        String hash = rs.getString("password");
        if (hash == null || hash.isEmpty()) {
            logger.warning("No password for player '" + name + "', skipping");
            return null;
        }
        long lastLogin = rs.getLong("lastLogin");
        long regDate = rs.getLong("regDate");
        return PlayerAuth.builder()
            .name(name)
            .realName(rs.getString("realName"))
            .password(new HashedPassword(hash))
            .lastIp(rs.getString("lastIp"))
            .registrationDate(regDate)
            .lastLogin(lastLogin > 0 ? lastLogin : null)
            .build();
    }
}
