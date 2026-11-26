package fr.xephi.authme.datasource;

import com.google.common.io.Files;
import fr.xephi.authme.ConsoleLogger;
import fr.xephi.authme.output.ConsoleLoggerFactory;
import fr.xephi.authme.settings.Settings;
import fr.xephi.authme.settings.properties.DatabaseSettings;
import fr.xephi.authme.util.FileUtils;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Migrates the SQLite database when necessary.
 */
class SqLiteMigrater {

    private final ConsoleLogger logger = ConsoleLoggerFactory.get(SqLiteMigrater.class);
    private final File dataFolder;
    private final String databaseName;
    private final String tableName;
    private final Columns col;

    SqLiteMigrater(Settings settings, File dataFolder) {
        this.dataFolder = dataFolder;
        this.databaseName = settings.getProperty(DatabaseSettings.MYSQL_DATABASE);
        this.tableName = settings.getProperty(DatabaseSettings.MYSQL_TABLE);
        this.col = new Columns(settings);
    }

    /**
     * Returns whether the database needs to be migrated.
     * <p>
     * Background: Before commit 22911a0 (July 2016), new SQLite databases initialized the last IP column to be NOT NULL
     * without a default value. Allowing the last IP to be null (#792) is therefore not compatible.
     *
     * @param metaData the database meta data
     * @param tableName the table name (SQLite file name)
     * @param col column names configuration
     * @return true if a migration is necessary, false otherwise
     */
    static boolean isMigrationRequired(DatabaseMetaData metaData, String tableName, Columns col) throws SQLException {
        return SqlDataSourceUtils.isNotNullColumn(metaData, tableName, col.LAST_IP)
            && SqlDataSourceUtils.getColumnDefaultValue(metaData, tableName, col.LAST_IP) == null;
    }

    /**
     * Migrates the given SQLite instance.
     *
     * @param sqLite the instance to migrate
     */
    void performMigration(SQLite sqLite) throws SQLException {
        logger.warning("YOUR SQLITE DATABASE NEEDS MIGRATING! DO NOT TURN OFF YOUR SERVER");

        String backupName = createBackup();
        logger.info("Made a backup of your database at 'backups/" + backupName + "'");

        recreateDatabaseWithNewDefinitions(sqLite);
        logger.info("SQLite database migrated successfully");
    }

    private String createBackup() {
        File backupDirectory = new File(dataFolder, "backups");
        FileUtils.createDirectory(backupDirectory);

        String backupName = "backup-" + databaseName + FileUtils.createCurrentTimeString() + ".db";
        try {
            Files.copy(new File(dataFolder, databaseName + ".db"), new File(backupDirectory, backupName));
            return backupName;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create SQLite backup before migration", e);
        }
    }

    /**
     * Renames the current database, creates a new database under the name and copies the data
     * from the renamed database to the newly created one. This is necessary because SQLite
     * does not support dropping or modifying a column.
     *
     * @param sqLite the SQLite instance to migrate
     */
    // cf. https://stackoverflow.com/questions/805363/how-do-i-rename-a-column-in-a-sqlite-database-table
    private void recreateDatabaseWithNewDefinitions(SQLite sqLite) throws SQLException {
        String tempTable = "tmp_" + tableName;
        Connection connection;
        try {
            Field connectionField = SQLite.class.getDeclaredField("con");
            connectionField.setAccessible(true);
            connection = (Connection) connectionField.get(sqLite);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalStateException("Failed to get the connection from SQLite", e);
        }
        try (Statement st = connection.createStatement()) {
            st.execute("ALTER TABLE " + tableName + " RENAME TO " + tempTable + ";");
        }

        sqLite.reload();

        String baseColumns = col.ID + ", " + col.NAME + ", " + col.REAL_NAME + ", " + col.PASSWORD;
        String locationColumns = col.LASTLOC_X + ", " + col.LASTLOC_Y + ", " + col.LASTLOC_Z + ", "
            + col.LASTLOC_WORLD + ", " + col.LASTLOC_PITCH + ", " + col.LASTLOC_YAW;
        try (Statement st = connection.createStatement()) {
            String copySql = "INSERT INTO " + tableName + " (" + baseColumns + ", " + col.LAST_IP + ", "
                + col.LAST_LOGIN + ", " + col.REGISTRATION_IP + ", " + col.REGISTRATION_DATE + ", " + locationColumns
                + ", " + col.EMAIL + ", " + col.IS_LOGGED + ") SELECT " + baseColumns + ", CASE WHEN " + col.LAST_IP
                + " = '127.0.0.1' OR " + col.LAST_IP + " = '' THEN NULL else " + col.LAST_IP + " END, "
                + col.LAST_LOGIN + ", " + col.REGISTRATION_IP + ", " + col.REGISTRATION_DATE + ", " + locationColumns
                + ", CASE WHEN " + col.EMAIL + " = 'your@email.com' THEN NULL ELSE " + col.EMAIL + " END, "
                + col.IS_LOGGED + " FROM " + tempTable + ";";
            int insertedEntries = st.executeUpdate(copySql);
            logger.info("Copied over " + insertedEntries + " from the old table to the new one");

            st.execute("DROP TABLE " + tempTable + ";");
        }
    }
}
