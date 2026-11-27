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
import java.util.Locale;

/**
 * Converts data from LimboAuth to AuthMe. Reads {@code plugins/limboauth/config.yml}:
 * {@code database.storage-type} may be {@code SQLITE} (file at
 * {@code plugins/limboauth/limboauth.db}), {@code MYSQL}/{@code MARIADB}/{@code POSTGRESQL}
 * (shared pool) or {@code H2} (unsupported). LimboAuth uses BCrypt, so set
 * {@code passwordHash: BCRYPT} in AuthMe before converting.
 */
public class LimboAuthConverter extends AbstractYamlPluginConverter {

    private static final String CONFIG_PATH = "plugins/limboauth/config.yml";
    private static final String SQLITE_DB_PATH = "plugins/limboauth/limboauth.db";
    private static final String QUERY =
        "SELECT NICKNAME, LOWERCASENICKNAME, HASH, IP, REGDATE, LOGINDATE, UUID, PREMIUMUUID, TOTPTOKEN FROM AUTH";

    private static final ConsoleLogger logger = ConsoleLoggerFactory.get(LimboAuthConverter.class);

    @Inject
    LimboAuthConverter(DataSource dataSource) {
        super(dataSource, CONFIG_PATH, SQLITE_DB_PATH, "LimboAuth", logger);
    }

    @Override
    protected Backend getBackend(YamlConfiguration config) {
        switch (readDbType(config, "database.storage-type")) {
            case "SQLITE":
                return Backend.SQLITE;
            case "MYSQL":
            case "MARIADB":
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
        return "LimboAuth conversion: database type '" + readDbType(config, "database.storage-type")
            + "' is not supported. "
            + "Reconfigure LimboAuth to use SQLite or MySQL/MariaDB/PostgreSQL, migrate the data, "
            + "then re-run the conversion.";
    }

    @Override
    protected String getName(ResultSet rs) throws SQLException {
        String realName = rs.getString("NICKNAME");
        String name = rs.getString("LOWERCASENICKNAME");
        if (name == null || name.isEmpty()) {
            name = realName != null ? realName.toLowerCase(Locale.ROOT) : null;
        }
        return name;
    }

    @Override
    protected PlayerAuth buildAuth(ResultSet rs, String name) throws SQLException {
        String realName = rs.getString("NICKNAME");
        if (realName == null) {
            realName = name;
        }
        String hash = rs.getString("HASH");
        if (hash == null || hash.isEmpty()) {
            logger.warning("No hash for player '" + name + "', skipping");
            return null;
        }
        long regDate = rs.getLong("REGDATE");
        long loginDate = rs.getLong("LOGINDATE");
        return PlayerAuth.builder()
            .name(name)
            .realName(realName)
            .password(new HashedPassword(hash))
            .lastIp(rs.getString("IP"))
            .registrationDate(regDate)
            .lastLogin(loginDate > 0 ? loginDate : null)
            .totpKey(rs.getString("TOTPTOKEN"))
            .uuid(UuidUtils.parseUuidSafely(rs.getString("UUID")))
            .premiumUuid(UuidUtils.parseUuidSafely(rs.getString("PREMIUMUUID")))
            .build();
    }
}
