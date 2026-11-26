package fr.xephi.authme.datasource.converter;

import fr.xephi.authme.ConsoleLogger;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.security.crypts.HashedPassword;
import fr.xephi.authme.util.UuidUtils;
import org.bukkit.command.CommandSender;

import java.io.File;
import java.sql.ResultSet;
import java.sql.SQLException;

import static fr.xephi.authme.util.Utils.logAndSendMessage;

/**
 * Shared conversion flow for plugins storing accounts in the {@code librepremium_data} table.
 * LibreLogin and NexAuth share the config layout and columns; they differ only in their
 * config path, type identifiers, algorithm name and timestamp representation.
 */
abstract class AbstractLibrePremiumConverter implements Converter {

    private static final String DEFAULT_SQLITE_FILENAME = "user-data.db";
    private static final String QUERY = "SELECT last_nickname, hashed_password, salt, algo, "
        + "ip, email, joined, last_seen, uuid, premium_uuid, secret FROM librepremium_data";

    private final DataSource dataSource;
    private final ConsoleLogger logger;
    private final String configPath;
    private final String pluginName;
    private final String typePrefix;
    private final String sqliteFolder;
    private final String argonAlgo;

    AbstractLibrePremiumConverter(DataSource dataSource, ConsoleLogger logger, String configPath,
                                  String pluginName, String typePrefix, String sqliteFolder, String argonAlgo) {
        this.dataSource = dataSource;
        this.logger = logger;
        this.configPath = configPath;
        this.pluginName = pluginName;
        this.typePrefix = typePrefix;
        this.sqliteFolder = sqliteFolder;
        this.argonAlgo = argonAlgo;
    }

    @Override
    public void execute(CommandSender sender) {
        File configFile = new File(configPath);
        if (!configFile.exists()) {
            logAndSendMessage(sender, pluginName + " conversion failed: config not found at " + configPath);
            return;
        }

        String dbType = ConverterUtils.readHoconValue(configFile, "type", typePrefix + "sqlite");
        if ((typePrefix + "sqlite").equals(dbType)) {
            String sqliteFilename = ConverterUtils.readHoconValue(configFile, "path", DEFAULT_SQLITE_FILENAME);
            File dbFile = new File(sqliteFolder + sqliteFilename);
            new SqliteImportTask(dataSource, dbFile, QUERY, pluginName, logger, rs -> importRows(rs, sender),
                pluginName + " conversion failed: SQLite database not found at " + dbFile.getPath()).execute(sender);
        } else if ((typePrefix + "mysql").equals(dbType) || (typePrefix + "postgresql").equals(dbType)) {
            new SqlImportTask(dataSource, QUERY, pluginName, logger, rs -> importRows(rs, sender)).execute(sender);
        } else {
            logAndSendMessage(sender, pluginName + " conversion: database type '" + dbType + "' is not supported.");
        }
    }

    private void importRows(ResultSet rs, CommandSender sender) throws SQLException {
        long imported = 0;
        long skipped = 0;
        while (rs.next()) {
            String realName = rs.getString("last_nickname");
            String name = ConverterUtils.toLowerCaseOrNull(realName);
            if (name == null) {
                continue;
            }

            if (dataSource.isAuthAvailable(name)) {
                ++skipped;
                continue;
            }

            HashedPassword password = ConverterUtils.buildHashedPassword(
                rs.getString("hashed_password"), rs.getString("salt"), rs.getString("algo"),
                name, argonAlgo, pluginName, logger);
            if (password == null) {
                continue;
            }

            ImportDates dates = readDates(rs);
            PlayerAuth auth = PlayerAuth.builder()
                .name(name)
                .realName(realName)
                .password(password)
                .lastIp(rs.getString("ip"))
                .email(rs.getString("email"))
                .registrationDate(dates.registrationDate)
                .lastLogin(dates.lastLogin)
                .totpKey(rs.getString("secret"))
                .uuid(UuidUtils.parseUuidSafely(rs.getString("uuid")))
                .premiumUuid(UuidUtils.parseUuidSafely(rs.getString("premium_uuid")))
                .build();

            dataSource.saveAuth(auth);
            ++imported;
        }

        logAndSendMessage(sender, pluginName + " conversion: " + imported + " account(s) imported, "
            + skipped + " skipped (already exist)");
    }

    /**
     * Reads the registration and last-login timestamps, whose storage differs between plugins.
     *
     * @param rs the current result row
     * @return the dates to import
     * @throws SQLException on a database error
     */
    protected abstract ImportDates readDates(ResultSet rs) throws SQLException;

    /** Registration time and optional last-login time of an account. */
    protected static final class ImportDates {

        private final long registrationDate;
        private final Long lastLogin;

        protected ImportDates(long registrationDate, Long lastLogin) {
            this.registrationDate = registrationDate;
            this.lastLogin = lastLogin;
        }
    }
}
