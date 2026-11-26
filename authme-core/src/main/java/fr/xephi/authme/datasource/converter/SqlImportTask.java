package fr.xephi.authme.datasource.converter;

import fr.xephi.authme.ConsoleLogger;
import fr.xephi.authme.datasource.DataSource;
import org.bukkit.command.CommandSender;

/**
 * Reusable SQL import step for plugin converters: runs the query on AuthMe's connection pool
 * and passes the rows to the importer.
 */
final class SqlImportTask extends AbstractSqlPluginConverter {

    private final String query;
    private final String pluginName;
    private final ConsoleLogger logger;
    private final ResultSetImporter importer;

    SqlImportTask(DataSource dataSource, String query, String pluginName,
                  ConsoleLogger logger, ResultSetImporter importer) {
        super(dataSource);
        this.query = query;
        this.pluginName = pluginName;
        this.logger = logger;
        this.importer = importer;
    }

    @Override
    public void execute(CommandSender sender) {
        queryAndImport(sender, query, pluginName, logger, importer);
    }
}
