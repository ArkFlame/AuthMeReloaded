package fr.xephi.authme.datasource;

import fr.xephi.authme.ConsoleLogger;
import fr.xephi.authme.output.ConsoleLoggerFactory;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;

/**
 * Performs migrations on the MySQL data source if necessary.
 */
final class MySqlMigrater {

    private static final ConsoleLogger logger = ConsoleLoggerFactory.get(MySqlMigrater.class);

    private MySqlMigrater() {
    }

    /**
     * Changes the last IP column to be nullable if it has a NOT NULL constraint without a default value.
     */
    static void migrateLastIpColumn(Statement st, DatabaseMetaData metaData,
                                    String tableName, Columns col) throws SQLException {
        if (SqlDataSourceUtils.isNotNullColumn(metaData, tableName, col.LAST_IP)
            && SqlDataSourceUtils.getColumnDefaultValue(metaData, tableName, col.LAST_IP) == null) {
            st.execute(String.format(
                "ALTER TABLE %s MODIFY %s VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin", tableName, col.LAST_IP));
            logger.info("Changed last login column to allow NULL values. Please verify the registration feature "
                + "if you are hooking into a forum.");
        }
    }

    /**
     * Migrates the last login column to bigint if it is still an int.
     */
    static void migrateLastLoginColumn(Statement st, DatabaseMetaData metaData,
                                       String tableName, Columns col) throws SQLException {
        int columnType;
        try (ResultSet rs = metaData.getColumns(null, null, tableName, col.LAST_LOGIN)) {
            if (!rs.next()) {
                logger.warning("Could not get LAST_LOGIN meta data. This should never happen!");
                return;
            }
            columnType = rs.getInt("DATA_TYPE");
        }

        if (columnType == Types.INTEGER) {
            logger.info("Migrating lastlogin column from int to bigint");
            st.execute(String.format("ALTER TABLE %s MODIFY %s BIGINT;", tableName, col.LAST_LOGIN));

            // Migrate plausible timestamps (2010-01-01 to 2017-12-31) from seconds to milliseconds
            int changedRows = st.executeUpdate(String.format(
                "UPDATE %s SET %s = %s * 1000 WHERE %s > %d AND %s < %d;", tableName, col.LAST_LOGIN,
                col.LAST_LOGIN, col.LAST_LOGIN, 1262304000, col.LAST_LOGIN, 1514678400));

            logger.warning("You may have entries with invalid timestamps. Please check your data "
                + "before purging. " + changedRows + " rows were migrated from seconds to milliseconds.");
        }
    }

    /**
     * Creates the column for registration date and sets all entries to the current timestamp.
     */
    static void addRegistrationDateColumn(Statement st, String tableName, Columns col) throws SQLException {
        st.executeUpdate("ALTER TABLE " + tableName
            + " ADD COLUMN " + col.REGISTRATION_DATE + " BIGINT NOT NULL DEFAULT 0;");

        // Use the timestamp from Java to avoid timezone issues in case JVM and database are out of sync
        long currentTimestamp = System.currentTimeMillis();
        int updatedRows = st.executeUpdate(String.format("UPDATE %s SET %s = %d;",
            tableName, col.REGISTRATION_DATE, currentTimestamp));
        logger.info("Created column '" + col.REGISTRATION_DATE + "' and set the current timestamp, "
            + currentTimestamp + ", to all " + updatedRows + " rows");
    }
}
