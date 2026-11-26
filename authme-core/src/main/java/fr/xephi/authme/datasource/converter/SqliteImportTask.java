package fr.xephi.authme.datasource.converter;

import fr.xephi.authme.ConsoleLogger;
import fr.xephi.authme.datasource.DataSource;
import org.bukkit.command.CommandSender;

import java.io.File;

import static fr.xephi.authme.util.Utils.logAndSendMessage;

/**
 * Reusable SQLite import step for plugin converters: checks that the database file exists,
 * then runs the query and passes the rows to the importer.
 */
final class SqliteImportTask extends AbstractSqlitePluginConverter {

    private final File dbFile;
    private final String query;
    private final String pluginName;
    private final ConsoleLogger logger;
    private final ResultSetImporter importer;
    private final String missingFileMessage;

    SqliteImportTask(DataSource dataSource, File dbFile, String query, String pluginName,
                     ConsoleLogger logger, ResultSetImporter importer, String missingFileMessage) {
        super(dataSource);
        this.dbFile = dbFile;
        this.query = query;
        this.pluginName = pluginName;
        this.logger = logger;
        this.importer = importer;
        this.missingFileMessage = missingFileMessage;
    }

    @Override
    public void execute(CommandSender sender) {
        if (!dbFile.exists()) {
            logAndSendMessage(sender, missingFileMessage);
            return;
        }
        queryAndImport(dbFile, sender, query, pluginName, logger, importer);
    }
}
