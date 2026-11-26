package fr.xephi.authme.datasource;

import fr.xephi.authme.ConsoleLogger;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.output.ConsoleLoggerFactory;
import fr.xephi.authme.util.UuidUtils;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Utilities for SQL data sources.
 */
public final class SqlDataSourceUtils {

    private static final ConsoleLogger logger = ConsoleLoggerFactory.get(SqlDataSourceUtils.class);

    private SqlDataSourceUtils() {
    }

    /**
     * Logs a SQL exception.
     *
     * @param e the exception to log
     */
    public static void logSqlException(SQLException e) {
        logger.logException("Error during SQL operation:", e);
    }

    /**
     * Returns the long value of a column, or null when appropriate. This method is necessary because
     * JDBC's {@link ResultSet#getLong} returns {@code 0} if the entry in the database is {@code null}.
     *
     * @param rs the result set to read from
     * @param columnName the name of the column to retrieve
     * @return the value (which may be null)
     */
    public static Long getNullableLong(ResultSet rs, String columnName) throws SQLException {
        long longValue = rs.getLong(columnName);
        return rs.wasNull() ? null : longValue;
    }

    /**
     * Builds a {@link PlayerAuth} from the current row of the given result set. The group id is only
     * read when {@code includeGroup} is true, since SQLite does not define a group column.
     *
     * @param row the result set positioned on the row to read
     * @param col the configured column names
     * @param includeGroup whether to read the group column
     * @return the player auth built from the row
     * @throws SQLException if the row cannot be read
     */
    public static PlayerAuth buildAuthFromResultSet(ResultSet row, Columns col, boolean includeGroup)
        throws SQLException {
        String salt = col.SALT.isEmpty() ? null : row.getString(col.SALT);
        int group = includeGroup && !col.GROUP.isEmpty() ? row.getInt(col.GROUP) : -1;
        UUID uuid = col.PLAYER_UUID.isEmpty()
            ? null : UuidUtils.parseUuidSafely(row.getString(col.PLAYER_UUID));
        UUID premiumUuid = col.PREMIUM_UUID.isEmpty()
            ? null : UuidUtils.parseUuidSafely(row.getString(col.PREMIUM_UUID));

        return PlayerAuth.builder()
            .name(row.getString(col.NAME))
            .realName(row.getString(col.REAL_NAME))
            .password(row.getString(col.PASSWORD), salt)
            .totpKey(row.getString(col.TOTP_KEY))
            .lastLogin(getNullableLong(row, col.LAST_LOGIN))
            .lastIp(row.getString(col.LAST_IP))
            .email(row.getString(col.EMAIL))
            .registrationDate(row.getLong(col.REGISTRATION_DATE))
            .registrationIp(row.getString(col.REGISTRATION_IP))
            .groupId(group)
            .locWorld(row.getString(col.LASTLOC_WORLD))
            .locX(row.getDouble(col.LASTLOC_X))
            .locY(row.getDouble(col.LASTLOC_Y))
            .locZ(row.getDouble(col.LASTLOC_Z))
            .locYaw(row.getFloat(col.LASTLOC_YAW))
            .locPitch(row.getFloat(col.LASTLOC_PITCH))
            .uuid(uuid)
            .premiumUuid(premiumUuid)
            .build();
    }

    /**
     * Returns whether the given column is absent from the table's metadata.
     *
     * @param metaData the database meta data
     * @param catalog the catalog to look in, or null for the default one
     * @param tableName the name of the table in which the column would be
     * @param columnName the name of the column to check
     * @return true if no metadata entry exists for the column
     */
    public static boolean isColumnMissing(DatabaseMetaData metaData, String catalog, String tableName,
                                          String columnName) throws SQLException {
        try (ResultSet rs = metaData.getColumns(catalog, null, tableName, columnName)) {
            return !rs.next();
        }
    }

    /**
     * Returns whether any index of the table covers the given column.
     *
     * @param metaData the database meta data
     * @param catalog the catalog to look in, or null for the default one
     * @param tableName the name of the table
     * @param columnName the name of the column to check
     * @return true if an index (including the primary key) contains the column
     */
    public static boolean isColumnIndexed(DatabaseMetaData metaData, String catalog, String tableName,
                                          String columnName) throws SQLException {
        try (ResultSet rs = metaData.getIndexInfo(catalog, null, tableName, false, false)) {
            while (rs.next()) {
                if (columnName.equalsIgnoreCase(rs.getString("COLUMN_NAME"))) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Returns whether the given column has a NOT NULL constraint.
     *
     * @param metaData the database meta data
     * @param tableName the name of the table in which the column is
     * @param columnName the name of the column to check
     * @return true if the column is NOT NULL, false otherwise
     */
    public static boolean isNotNullColumn(DatabaseMetaData metaData, String tableName,
                                          String columnName) throws SQLException {
        return readColumnData(metaData, tableName, columnName, "while checking for not-null constraint", rs -> {
            int nullableCode = rs.getInt("NULLABLE");
            if (nullableCode == DatabaseMetaData.columnNullableUnknown) {
                logger.warning("Unknown nullable status for column '" + columnName + "'");
            }
            return nullableCode == DatabaseMetaData.columnNoNulls;
        });
    }

    /**
     * Returns the default value of a column (as per its SQL definition).
     *
     * @param metaData the database meta data
     * @param tableName the name of the table in which the column is
     * @param columnName the name of the column to check
     * @return the default value of the column (may be null)
     */
    public static Object getColumnDefaultValue(DatabaseMetaData metaData, String tableName,
                                               String columnName) throws SQLException {
        return readColumnData(metaData, tableName, columnName, "while checking its default value",
            rs -> rs.getObject("COLUMN_DEF"));
    }

    /**
     * Returns the size of a column (as per its SQL definition).
     *
     * @param metaData the database meta data
     * @param tableName the name of the table in which the column is
     * @param columnName the name of the column to check
     * @return the size of the column
     */
    public static int getColumnSize(DatabaseMetaData metaData, String tableName,
                                    String columnName) throws SQLException {
        return readColumnData(metaData, tableName, columnName, "while checking its size",
            rs -> rs.getInt("COLUMN_SIZE"));
    }

    /**
     * Reads a column's metadata row, closing the result set whether the read succeeds or fails.
     */
    private static <T> T readColumnData(DatabaseMetaData metaData, String tableName, String columnName,
                                        String purpose, ColumnDataReader<T> reader) throws SQLException {
        try (ResultSet rs = metaData.getColumns(null, null, tableName, columnName)) {
            if (!rs.next()) {
                throw new IllegalStateException("Did not find meta data for column '" + columnName + "' " + purpose);
            }
            return reader.read(rs);
        }
    }

    @FunctionalInterface
    private interface ColumnDataReader<T> {

        T read(ResultSet rs) throws SQLException;
    }
}
