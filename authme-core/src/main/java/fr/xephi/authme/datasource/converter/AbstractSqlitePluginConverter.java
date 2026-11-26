package fr.xephi.authme.datasource.converter;

import fr.xephi.authme.ConsoleLogger;
import fr.xephi.authme.datasource.DataSource;
import org.bukkit.command.CommandSender;

import java.io.File;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Base class for converters that read from an external plugin's SQLite database file.
 */
abstract class AbstractSqlitePluginConverter implements Converter {

    private final DataSource dataSource;

    AbstractSqlitePluginConverter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    protected DataSource getDataSource() {
        return dataSource;
    }

    /**
     * Runs the given query on the SQLite file and hands the result rows to the importer.
     * SQL errors are reported to the sender and the log instead of being propagated.
     *
     * @param dbFile the SQLite database file
     * @param sender the command sender to report errors to
     * @param query the query to execute
     * @param pluginName the source plugin name, used in error messages
     * @param logger logger for SQL errors
     * @param importer the consumer of the result rows
     */
    protected void queryAndImport(File dbFile, CommandSender sender, String query, String pluginName,
                                  ConsoleLogger logger, ResultSetImporter importer) {
        ConverterUtils.runQuery(() -> {
            try {
                Class.forName("org.sqlite.JDBC");
            } catch (ClassNotFoundException e) {
                throw new SQLException("SQLite JDBC driver not available", e);
            }
            return DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
        }, sender, query, pluginName, logger, importer);
    }
}
