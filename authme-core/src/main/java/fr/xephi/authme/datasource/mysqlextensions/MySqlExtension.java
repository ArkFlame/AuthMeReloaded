package fr.xephi.authme.datasource.mysqlextensions;

import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.datasource.Columns;
import fr.xephi.authme.security.crypts.HashedPassword;
import fr.xephi.authme.settings.Settings;
import fr.xephi.authme.settings.properties.DatabaseSettings;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.OptionalInt;

/**
 * Extension for the MySQL data source for forums. For certain password hashes (e.g. phpBB), we want
 * to hook into the forum board and execute some actions specific to the forum software.
 */
public abstract class MySqlExtension {

    protected final Columns col;
    protected final String tableName;
    private final String retrieveIdSql;

    MySqlExtension(Settings settings, Columns col) {
        this.col = col;
        this.tableName = settings.getProperty(DatabaseSettings.MYSQL_TABLE);
        this.retrieveIdSql = "SELECT " + col.ID + " FROM " + tableName + " WHERE " + col.NAME + "=?;";
    }

    /**
     * Performs additional actions when a new player is saved.
     *
     * @param auth the player auth that has been saved
     * @param con connection to the sql table
     * @throws SQLException .
     */
    public void saveAuth(PlayerAuth auth, Connection con) throws SQLException {
        // extend for custom behavior
    }

    /**
     * Writes properties to the given PlayerAuth object that need to be retrieved in a specific manner
     * when a PlayerAuth object is read from the table.
     *
     * @param auth the player auth object to extend
     * @param id the database id of the player auth entry
     * @param con connection to the sql table
     * @throws SQLException .
     */
    public void extendAuth(PlayerAuth auth, int id, Connection con) throws SQLException {
        // extend for custom behavior
    }

    /**
     * Performs additional actions when a user's password is changed.
     *
     * @param user the name of the player (lowercase)
     * @param password the new password to set
     * @param con connection to the sql table
     * @throws SQLException .
     */
    public void changePassword(String user, HashedPassword password, Connection con) throws SQLException {
        // extend for custom behavior
    }

    /**
     * Performs additional actions when a player is removed from the database.
     *
     * @param user the user to remove
     * @param con connection to the sql table
     * @throws SQLException .
     */
    public void removeAuth(String user, Connection con) throws SQLException {
        // extend for custom behavior
    }

    /**
     * Fetches the database ID of the given name from the database.
     *
     * @param name the name to get the ID for
     * @param con connection to the sql table
     * @return id of the playerAuth, or empty OptionalInt if the name is not registered
     * @throws SQLException .
     */
    protected OptionalInt retrieveIdFromTable(String name, Connection con) throws SQLException {
        try (PreparedStatement pst = con.prepareStatement(retrieveIdSql)) {
            pst.setString(1, name);
            try (ResultSet rs = pst.executeQuery()) {
                return rs.next() ? OptionalInt.of(rs.getInt(col.ID)) : OptionalInt.empty();
            }
        }
    }

    /**
     * Sets the given columns of the account's row to an int value. Implemented with primitive value types
     * to avoid boxing when the value comes from a configuration property or a long/int computation.
     *
     * @param fullTableName table name as written after {@code UPDATE}
     * @param qualifiedTableName table name used to qualify the columns in the {@code SET} clause
     * @param columns the columns to update
     * @param value the value to set the columns to
     * @param name the name of the account to update
     * @param con connection to the sql table
     * @throws SQLException .
     */
    protected void setIntColumns(String fullTableName, String qualifiedTableName, String[] columns,
                                 int value, String name, Connection con) throws SQLException {
        setColumns(fullTableName, qualifiedTableName, columns, pst -> pst.setInt(1, value), name, con);
    }

    /**
     * Sets the given columns of the account's row to a long value.
     *
     * @param fullTableName table name as written after {@code UPDATE}
     * @param qualifiedTableName table name used to qualify the columns in the {@code SET} clause
     * @param columns the columns to update
     * @param value the value to set the columns to
     * @param name the name of the account to update
     * @param con connection to the sql table
     * @throws SQLException .
     */
    protected void setLongColumns(String fullTableName, String qualifiedTableName, String[] columns,
                                  long value, String name, Connection con) throws SQLException {
        setColumns(fullTableName, qualifiedTableName, columns, pst -> pst.setLong(1, value), name, con);
    }

    /**
     * Sets the given columns of the account's row to a string value.
     *
     * @param fullTableName table name as written after {@code UPDATE}
     * @param qualifiedTableName table name used to qualify the columns in the {@code SET} clause
     * @param columns the columns to update
     * @param value the value to set the columns to
     * @param name the name of the account to update
     * @param con connection to the sql table
     * @throws SQLException .
     */
    protected void setStringColumns(String fullTableName, String qualifiedTableName, String[] columns,
                                    String value, String name, Connection con) throws SQLException {
        setColumns(fullTableName, qualifiedTableName, columns, pst -> pst.setString(1, value), name, con);
    }

    /**
     * Updates every listed column to the value bound by the given setter.
     */
    private void setColumns(String fullTableName, String qualifiedTableName, String[] columns,
                            ValueSetter valueSetter, String name, Connection con) throws SQLException {
        String sqlPrefix = "UPDATE " + fullTableName + " SET " + qualifiedTableName + ".";
        String sqlSuffix = "=? WHERE " + col.NAME + "=?;";
        for (String column : columns) {
            try (PreparedStatement pst = con.prepareStatement(sqlPrefix + column + sqlSuffix)) {
                valueSetter.set(pst);
                pst.setString(2, name);
                pst.executeUpdate();
            }
        }
    }

    @FunctionalInterface
    private interface ValueSetter {

        void set(PreparedStatement pst) throws SQLException;
    }
}
