package fr.xephi.authme.datasource;

import com.zaxxer.hikari.HikariDataSource;
import ch.jalu.datasourcecolumns.data.DataSourceValue;
import ch.jalu.datasourcecolumns.data.DataSourceValueImpl;
import ch.jalu.datasourcecolumns.data.DataSourceValues;
import ch.jalu.datasourcecolumns.predicate.AlwaysTruePredicate;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.datasource.columnshandler.AuthMeColumns;
import fr.xephi.authme.datasource.columnshandler.DataSourceColumn;
import fr.xephi.authme.datasource.mysqlextensions.MySqlExtension;
import fr.xephi.authme.datasource.columnshandler.AuthMeColumnsHandler;
import fr.xephi.authme.security.crypts.HashedPassword;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static ch.jalu.datasourcecolumns.data.UpdateValues.with;
import static ch.jalu.datasourcecolumns.predicate.StandardPredicates.eq;
import static ch.jalu.datasourcecolumns.predicate.StandardPredicates.eqIgnoreCase;
import static ch.jalu.datasourcecolumns.predicate.StandardPredicates.isNotNull;
import static fr.xephi.authme.datasource.SqlDataSourceUtils.logSqlException;

/**
 * Common type for SQL-based data sources. Classes implementing this
 * must ensure that {@link #columnsHandler} is initialized on creation.
 */
public abstract class AbstractSqlDataSource implements DataSource {

    protected AuthMeColumnsHandler columnsHandler;
    protected HikariDataSource ds;
    protected String tableName;
    protected Columns col;
    protected List<String> columnOthers;
    protected MySqlExtension sqlExtension;

    // Built once after the table and column names are known, then reused for every call.
    protected String findAllSql;
    protected String deleteByNameSql;
    protected String findLoggedWithEmptyEmailSql;
    protected String findRecentLoginsSql;
    protected String updateTotpSql;
    private String findAuthSql;
    private String findRecordsToPurgeSql;

    /**
     * Initializes the SQL strings shared by all SQL data sources. Must be called once the
     * table name and the column names have been assigned.
     *
     * @param authNameCondition the dialect-specific condition for matching a name in a lookup
     * @param purgeFunction the SQL aggregate used to pick the latest activity timestamp when purging
     */
    protected void initSqlStrings(String authNameCondition, String purgeFunction) {
        findAllSql = "SELECT * FROM " + tableName + ";";
        deleteByNameSql = "DELETE FROM " + tableName + " WHERE " + col.NAME + "=?;";
        findLoggedWithEmptyEmailSql = "SELECT " + col.REAL_NAME + " FROM " + tableName + " WHERE "
            + col.IS_LOGGED + " = 1 AND (" + col.EMAIL + " = 'your@email.com' OR " + col.EMAIL + " IS NULL);";
        findRecentLoginsSql = "SELECT * FROM " + tableName + " ORDER BY " + col.LAST_LOGIN + " DESC LIMIT 10;";
        updateTotpSql = "UPDATE " + tableName + " SET " + col.TOTP_KEY + " = ? WHERE " + col.NAME + " = ?";
        findAuthSql = "SELECT * FROM " + tableName + " WHERE " + authNameCondition + ";";
        findRecordsToPurgeSql = "SELECT " + col.NAME + " FROM " + tableName + " WHERE "
            + purgeFunction + "( COALESCE(" + col.LAST_LOGIN + ", 0), COALESCE(" + col.REGISTRATION_DATE + ", 0)) < ?;";
    }

    protected String getFindAuthSql() {
        return findAuthSql;
    }

    protected String getFindRecordsToPurgeSql() {
        return findRecordsToPurgeSql;
    }

    @Override
    public boolean isAuthAvailable(String user) {
        try {
            return columnsHandler.retrieve(user, AuthMeColumns.NAME).rowExists();
        } catch (SQLException e) {
            logSqlException(e);
            return false;
        }
    }

    @Override
    public HashedPassword getPassword(String user) {
        try {
            DataSourceValues values = columnsHandler.retrieve(user, AuthMeColumns.PASSWORD, AuthMeColumns.SALT);
            return values.rowExists()
                ? new HashedPassword(values.get(AuthMeColumns.PASSWORD), values.get(AuthMeColumns.SALT))
                : null;
        } catch (SQLException e) {
            logSqlException(e);
            return null;
        }
    }

    @Override
    public boolean saveAuth(PlayerAuth auth) {
        return columnsHandler.insert(auth,
            AuthMeColumns.NAME, AuthMeColumns.NICK_NAME, AuthMeColumns.PASSWORD, AuthMeColumns.SALT,
            AuthMeColumns.EMAIL, AuthMeColumns.REGISTRATION_DATE, AuthMeColumns.REGISTRATION_IP,
            AuthMeColumns.UUID);
    }

    @Override
    public boolean hasSession(String user) {
        return isFlagSet(user, AuthMeColumns.HAS_SESSION);
    }

    @Override
    public boolean updateSession(PlayerAuth auth) {
        return columnsHandler.update(auth, AuthMeColumns.LAST_IP, AuthMeColumns.LAST_LOGIN, AuthMeColumns.NICK_NAME);
    }

    @Override
    public boolean updatePassword(PlayerAuth auth) {
        return updatePassword(auth.getNickname(), auth.getPassword());
    }

    @Override
    public boolean updatePassword(String user, HashedPassword password) {
        return columnsHandler.update(user,
            with(AuthMeColumns.PASSWORD, password.getHash())
            .and(AuthMeColumns.SALT, password.getSalt()).build());
    }

    @Override
    public boolean updateQuitLoc(PlayerAuth auth) {
        return columnsHandler.update(auth,
            AuthMeColumns.LOCATION_X, AuthMeColumns.LOCATION_Y, AuthMeColumns.LOCATION_Z,
            AuthMeColumns.LOCATION_WORLD, AuthMeColumns.LOCATION_YAW, AuthMeColumns.LOCATION_PITCH);
    }

    @Override
    public List<String> getAllAuthsByIp(String ip) {
        try {
            return columnsHandler.retrieve(eq(AuthMeColumns.LAST_IP, ip), AuthMeColumns.NAME);
        } catch (SQLException e) {
            logSqlException(e);
            return Collections.emptyList();
        }
    }

    @Override
    public int countAuthsByEmail(String email) {
        return columnsHandler.count(eqIgnoreCase(AuthMeColumns.EMAIL, email));
    }

    @Override
    public boolean updateEmail(PlayerAuth auth) {
        return columnsHandler.update(auth, AuthMeColumns.EMAIL);
    }

    @Override
    public boolean isLogged(String user) {
        return isFlagSet(user, AuthMeColumns.IS_LOGGED);
    }

    @Override
    public void setLogged(String user) {
        setFlag(user, AuthMeColumns.IS_LOGGED, 1);
    }

    @Override
    public void setUnlogged(String user) {
        setFlag(user, AuthMeColumns.IS_LOGGED, 0);
    }

    @Override
    public void grantSession(String user) {
        setFlag(user, AuthMeColumns.HAS_SESSION, 1);
    }

    @Override
    public void revokeSession(String user) {
        setFlag(user, AuthMeColumns.HAS_SESSION, 0);
    }

    @Override
    public void purgeLogged() {
        columnsHandler.update(eq(AuthMeColumns.IS_LOGGED, 1), AuthMeColumns.IS_LOGGED, 0);
    }

    @Override
    public int getAccountsRegistered() {
        return columnsHandler.count(new AlwaysTruePredicate<>());
    }

    @Override
    public boolean updatePremiumUuid(PlayerAuth auth) {
        return columnsHandler.update(auth, AuthMeColumns.PREMIUM_UUID);
    }

    @Override
    public List<String> getPremiumUsernames() {
        try {
            return columnsHandler.retrieve(isNotNull(AuthMeColumns.PREMIUM_UUID), AuthMeColumns.NAME);
        } catch (SQLException e) {
            logSqlException(e);
            return Collections.emptyList();
        }
    }

    @Override
    public boolean updateRealName(String user, String realName) {
        return columnsHandler.update(user, AuthMeColumns.NICK_NAME, realName);
    }

    @Override
    public DataSourceValue<String> getEmail(String user) {
        try {
            return columnsHandler.retrieve(user, AuthMeColumns.EMAIL);
        } catch (SQLException e) {
            logSqlException(e);
            return DataSourceValueImpl.unknownRow();
        }
    }

    /**
     * Reads the names of all records to purge. The caller keeps ownership of the connection.
     */
    protected Set<String> findRecordsToPurge(Connection con, String sql, long until) throws SQLException {
        Set<String> names = new HashSet<>();
        try (PreparedStatement pst = con.prepareStatement(sql)) {
            pst.setLong(1, until);
            try (ResultSet rs = pst.executeQuery()) {
                while (rs.next()) {
                    names.add(rs.getString(col.NAME));
                }
            }
        }
        return names;
    }

    /**
     * Deletes all records whose names are in the given collection. The caller keeps ownership of the connection.
     */
    protected void deleteRecords(Connection con, String sql, Collection<String> names) throws SQLException {
        if (names.isEmpty()) {
            return;
        }
        try (PreparedStatement pst = con.prepareStatement(sql)) {
            for (String name : names) {
                pst.setString(1, name.toLowerCase(Locale.ROOT));
                pst.addBatch();
            }
            pst.executeBatch();
        }
    }

    /**
     * Reads the real names of logged-in players with an empty email address.
     */
    protected List<String> findLoggedWithEmptyMail(Connection con, String sql) throws SQLException {
        List<String> players = new ArrayList<>();
        try (PreparedStatement pst = con.prepareStatement(sql); ResultSet rs = pst.executeQuery()) {
            while (rs.next()) {
                players.add(rs.getString(1));
            }
        }
        return players;
    }

    /**
     * Updates the TOTP key of the given user.
     */
    protected boolean updateTotpKey(Connection con, String sql, String user, String totpKey) throws SQLException {
        try (PreparedStatement pst = con.prepareStatement(sql)) {
            pst.setString(1, totpKey);
            pst.setString(2, user.toLowerCase(Locale.ROOT));
            pst.executeUpdate();
            return true;
        }
    }

    /**
     * Adds a column with the given definition to this data source's table.
     */
    protected void addColumn(Statement st, String column, String definition) throws SQLException {
        alterTable(st, "ADD COLUMN " + column + " " + definition);
    }

    /**
     * Adds a column with the given definition to this data source's table if it is not there yet.
     */
    protected void addColumnIfAbsent(DatabaseMetaData md, Statement st, String column, String definition)
        throws SQLException {
        if (isColumnMissing(md, column)) {
            addColumn(st, column, definition);
        }
    }

    /** Adds the configured UUID columns to the table when they are not there yet. */
    protected void addUuidColumnsIfAbsent(DatabaseMetaData md, Statement st) throws SQLException {
        addUuidColumnIfAbsent(md, st, col.PLAYER_UUID);
        addUuidColumnIfAbsent(md, st, col.PREMIUM_UUID);
    }

    private void addUuidColumnIfAbsent(DatabaseMetaData md, Statement st, String uuidColumn) throws SQLException {
        if (!uuidColumn.isEmpty()) {
            addColumnIfAbsent(md, st, uuidColumn, "VARCHAR(36)");
        }
    }

    /**
     * Executes an ALTER TABLE statement against this data source's table.
     */
    protected void alterTable(Statement st, String alteration) throws SQLException {
        st.executeUpdate("ALTER TABLE " + tableName + " " + alteration);
    }

    /**
     * Returns whether the given column is absent from the table metadata. Each database decides
     * which catalog to inspect and how to normalize the column name before the lookup.
     */
    protected abstract boolean isColumnMissing(DatabaseMetaData metaData, String columnName) throws SQLException;

    /**
     * Returns whether the given integer flag column is set to 1 for the user.
     */
    private boolean isFlagSet(String user, DataSourceColumn<Integer> column) {
        try {
            DataSourceValue<Integer> result = columnsHandler.retrieve(user, column);
            return result.rowExists() && Integer.valueOf(1).equals(result.getValue());
        } catch (SQLException e) {
            logSqlException(e);
            return false;
        }
    }

    /**
     * Updates the given integer flag to the supplied value for the user.
     */
    private void setFlag(String user, DataSourceColumn<Integer> column, int value) {
        columnsHandler.update(user, column, value);
    }

    abstract String getJdbcUrl(String host, String port, String database);
}
