package fr.xephi.authme.datasource.converter;

import fr.xephi.authme.ConsoleLogger;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.datasource.SqlConnectionSource;
import org.bukkit.command.CommandSender;

import java.sql.SQLException;

/**
 * Base class for converters that read from an external plugin's MySQL/MariaDB table.
 * <p>
 * The source plugin is expected to share the same MySQL/MariaDB database as AuthMe.
 * AuthMe's existing HikariCP connection pool is reused — no separate connection is opened.
 * SQLite data sources are not supported by this converter base class.
 */
abstract class AbstractSqlPluginConverter implements Converter {

    private final DataSource dataSource;

    AbstractSqlPluginConverter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Runs the given query on the shared connection pool and hands the result rows to the importer.
     * SQL errors are reported to the sender and the log instead of being propagated.
     * The query requires AuthMe to be configured with MySQL or MariaDB.
     *
     * @param sender the command sender to report errors to
     * @param query the query to execute
     * @param pluginName the source plugin name, used in error messages
     * @param logger logger for SQL errors
     * @param importer the consumer of the result rows
     */
    protected void queryAndImport(CommandSender sender, String query, String pluginName,
                                  ConsoleLogger logger, ResultSetImporter importer) {
        ConverterUtils.runQuery(() -> {
            if (dataSource instanceof SqlConnectionSource) {
                return ((SqlConnectionSource) dataSource).getConnection();
            }
            throw new SQLException(
                "This converter requires AuthMe to be configured with MySQL or MariaDB. "
                    + "SQLite is not supported as a shared data source for plugin converters.");
        }, sender, query, pluginName, logger, importer);
    }
}
