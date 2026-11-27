package fr.xephi.authme.datasource.converter;

import fr.xephi.authme.ConsoleLogger;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.datasource.SqlConnectionSource;
import org.bukkit.command.CommandSender;

import java.sql.SQLException;

/**
 * Reusable SQL import step: runs a query on AuthMe's shared connection pool and passes the rows
 * to the importer. The source plugin must share AuthMe's MySQL/MariaDB database; SQLite sources
 * are not supported. SQL errors are reported to the sender and the log instead of being propagated.
 */
final class SqlImportTask implements Converter {

    private final DataSource dataSource;
    private final String query;
    private final String pluginName;
    private final ConsoleLogger logger;
    private final ResultSetImporter importer;

    SqlImportTask(DataSource dataSource, String query, String pluginName,
                  ConsoleLogger logger, ResultSetImporter importer) {
        this.dataSource = dataSource;
        this.query = query;
        this.pluginName = pluginName;
        this.logger = logger;
        this.importer = importer;
    }

    @Override
    public void execute(CommandSender sender) {
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
