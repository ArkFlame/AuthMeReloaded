package fr.xephi.authme.datasource.converter;

import fr.xephi.authme.ConsoleLogger;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.datasource.DataSource;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.sql.ResultSet;
import java.sql.SQLException;

import static fr.xephi.authme.util.Utils.logAndSendMessage;

/**
 * Shared flow for converters whose storage backend is selected in a YAML config file:
 * config lookup, SQLite/shared-SQL dispatch and the account-import loop.
 */
abstract class AbstractYamlPluginConverter implements Converter {

    protected enum Backend { SQLITE, SQL, UNSUPPORTED }

    private final DataSource dataSource;
    private final String configPath;
    private final String sqliteDbPath;
    private final String pluginName;
    private final ConsoleLogger logger;

    protected AbstractYamlPluginConverter(DataSource dataSource, String configPath, String sqliteDbPath,
                                          String pluginName, ConsoleLogger logger) {
        this.dataSource = dataSource;
        this.configPath = configPath;
        this.sqliteDbPath = sqliteDbPath;
        this.pluginName = pluginName;
        this.logger = logger;
    }

    @Override
    public void execute(CommandSender sender) {
        File configFile = new File(configPath);
        if (!configFile.exists()) {
            logAndSendMessage(sender, pluginName + " conversion failed: config not found at " + configPath);
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        String query = getQuery(config);
        ResultSetImporter importer = rs -> importRows(rs, sender);
        switch (getBackend(config)) {
            case SQLITE:
                new SqliteImportTask(dataSource, new File(sqliteDbPath), query, pluginName, logger, importer,
                    pluginName + " conversion failed: SQLite database not found at " + sqliteDbPath).execute(sender);
                break;
            case SQL:
                new SqlImportTask(dataSource, query, pluginName, logger, importer).execute(sender);
                break;
            default:
                logAndSendMessage(sender, unsupportedMessage(config));
        }
    }

    protected abstract Backend getBackend(YamlConfiguration config);

    protected abstract String getQuery(YamlConfiguration config);

    protected abstract String unsupportedMessage(YamlConfiguration config);

    /** @return the lowercase account name for this row, or {@code null}/empty to skip it */
    protected abstract String getName(ResultSet rs) throws SQLException;

    /** @return the account to import, or {@code null} if the row has invalid data */
    protected abstract PlayerAuth buildAuth(ResultSet rs, String name) throws SQLException;

    private void importRows(ResultSet rs, CommandSender sender) throws SQLException {
        long imported = 0;
        long skipped = 0;
        while (rs.next()) {
            String name = getName(rs);
            if (name == null || name.isEmpty()) {
                continue;
            }
            if (dataSource.isAuthAvailable(name)) {
                ++skipped;
                continue;
            }
            PlayerAuth auth = buildAuth(rs, name);
            if (auth == null) {
                continue;
            }
            dataSource.saveAuth(auth);
            ++imported;
        }
        logAndSendMessage(sender, pluginName + " conversion: " + imported + " account(s) imported, "
            + skipped + " skipped (already exist)");
    }
}
