package fr.xephi.authme.datasource;

import com.zaxxer.hikari.pool.HikariPool.PoolInitializationException;
import fr.xephi.authme.ConsoleLogger;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.datasource.columnshandler.AuthMeColumnsHandler;
import fr.xephi.authme.datasource.mysqlextensions.MySqlExtensionsFactory;
import fr.xephi.authme.settings.Settings;
import fr.xephi.authme.settings.properties.DatabaseSettings;
import fr.xephi.authme.settings.properties.HooksSettings;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static fr.xephi.authme.datasource.SqlDataSourceUtils.logSqlException;

/**
 * Common implementation of the data source operations for SQL databases that obtain their
 * connections from a Hikari pool (MySQL, MariaDB and PostgreSQL). Subclasses take care of the
 * pool configuration and of creating/updating the database-specific table schema.
 */
public abstract class AbstractHikariSqlDataSource extends AbstractSqlDataSource implements SqlConnectionSource {

    private String updateOtherUsernamesSql;

    private String host;
    private String port;
    private String username;
    private String password;
    private String database;
    private int poolSize;
    private int maxLifetime;

    /**
     * Loads the connection settings shared by every Hikari-based SQL data source.
     */
    protected void setCommonParameters(Settings settings, MySqlExtensionsFactory extensionsFactory) {
        this.host = settings.getProperty(DatabaseSettings.MYSQL_HOST);
        this.port = settings.getProperty(DatabaseSettings.MYSQL_PORT);
        this.username = settings.getProperty(DatabaseSettings.MYSQL_USERNAME);
        this.password = settings.getProperty(DatabaseSettings.MYSQL_PASSWORD);
        this.database = settings.getProperty(DatabaseSettings.MYSQL_DATABASE);
        this.tableName = settings.getProperty(DatabaseSettings.MYSQL_TABLE);
        this.columnOthers = settings.getProperty(HooksSettings.MYSQL_OTHER_USERNAME_COLS);
        this.col = new Columns(settings);
        this.columnsHandler = AuthMeColumnsHandler.createForMySql(this::getConnection, settings);
        this.sqlExtension = extensionsFactory.buildExtension(col);
        this.poolSize = settings.getProperty(DatabaseSettings.MYSQL_POOL_SIZE);
        this.maxLifetime = settings.getProperty(DatabaseSettings.MYSQL_CONNECTION_MAX_LIFETIME);
        initSqlStrings(col.NAME + "=?", "GREATEST");
    }

    protected String getHost() {
        return host;
    }

    protected String getPort() {
        return port;
    }

    protected String getUsername() {
        return username;
    }

    protected String getPassword() {
        return password;
    }

    protected String getDatabase() {
        return database;
    }

    protected int getPoolSize() {
        return poolSize;
    }

    protected int getMaxLifetime() {
        return maxLifetime;
    }

    @Override
    protected void initSqlStrings(String authNameCondition, String purgeFunction) {
        super.initSqlStrings(authNameCondition, purgeFunction);
        updateOtherUsernamesSql = buildOtherUsernamesUpdateSql();
    }

    /**
     * Builds the single UPDATE statement for all configured extra username columns, or null if none are configured.
     */
    private String buildOtherUsernamesUpdateSql() {
        if (columnOthers.isEmpty()) {
            return null;
        }
        StringBuilder sql = new StringBuilder("UPDATE ").append(tableName).append(" SET ");
        for (int i = 0; i < columnOthers.size(); ++i) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append(columnOthers.get(i)).append("=?");
        }
        return sql.append(" WHERE ").append(col.NAME).append("=?;").toString();
    }

    @Override
    public Connection getConnection() throws SQLException {
        return ds.getConnection();
    }

    @Override
    public void closeConnection() {
        if (ds != null && !ds.isClosed()) {
            ds.close();
        }
    }

    /** Sets up the Hikari pool, logging configuration failures before rethrowing. */
    protected void setupPool(Runnable poolSetup, ConsoleLogger logger) {
        try {
            poolSetup.run();
        } catch (RuntimeException e) {
            if (e instanceof IllegalArgumentException) {
                logger.warning("Invalid database arguments! Please check your configuration!");
            } else if (e instanceof PoolInitializationException) {
                logger.warning("Can't initialize database connection! Please check your configuration!");
            }
            logger.warning("If this error persists, please report it to the developer!");
            logger.warning("Can't use the Hikari Connection Pool! Please, report this error to the developer!");
            throw e;
        }
    }

    /** Closes and re-creates the Hikari pool with the given setup. */
    protected void reloadPool(Runnable poolSetup, ConsoleLogger logger) {
        if (ds != null) {
            ds.close();
        }
        poolSetup.run();
        logger.info("Hikari ConnectionPool arguments reloaded!");
    }

    @Override
    public PlayerAuth getAuth(String user) {
        try (Connection con = getConnection(); PreparedStatement pst = con.prepareStatement(getFindAuthSql())) {
            pst.setString(1, user.toLowerCase(Locale.ROOT));
            try (ResultSet rs = pst.executeQuery()) {
                if (rs.next()) {
                    int id = rs.getInt(col.ID);
                    PlayerAuth auth = SqlDataSourceUtils.buildAuthFromResultSet(rs, col, true);
                    sqlExtension.extendAuth(auth, id, con);
                    return auth;
                }
            }
        } catch (SQLException ex) {
            logSqlException(ex);
        }
        return null;
    }

    @Override
    public boolean saveAuth(PlayerAuth auth) {
        // If the primary INSERT failed, there is no account to extend; reporting success would hide it.
        if (!super.saveAuth(auth)) {
            return false;
        }

        try (Connection con = getConnection()) {
            if (updateOtherUsernamesSql != null) {
                try (PreparedStatement pst = con.prepareStatement(updateOtherUsernamesSql)) {
                    int index = 1;
                    for (String ignored : columnOthers) {
                        pst.setString(index++, auth.getRealName());
                    }
                    pst.setString(index, auth.getNickname());
                    pst.executeUpdate();
                }
            }

            sqlExtension.saveAuth(auth, con);
            return true;
        } catch (SQLException ex) {
            logSqlException(ex);
        }
        return false;
    }

    @Override
    public Set<String> getRecordsToPurge(long until) {
        try (Connection con = getConnection()) {
            return findRecordsToPurge(con, getFindRecordsToPurgeSql(), until);
        } catch (SQLException ex) {
            logSqlException(ex);
        }
        return new HashSet<>();
    }

    @Override
    public boolean removeAuth(String user) {
        String name = user.toLowerCase(Locale.ROOT);
        try (Connection con = getConnection(); PreparedStatement pst = con.prepareStatement(deleteByNameSql)) {
            sqlExtension.removeAuth(name, con);
            pst.setString(1, name);
            pst.executeUpdate();
            return true;
        } catch (SQLException ex) {
            logSqlException(ex);
        }
        return false;
    }

    @Override
    public List<PlayerAuth> getAllAuths() {
        List<PlayerAuth> auths = new ArrayList<>();
        try (Connection con = getConnection(); PreparedStatement pst = con.prepareStatement(findAllSql)) {
            try (ResultSet rs = pst.executeQuery()) {
                while (rs.next()) {
                    PlayerAuth auth = SqlDataSourceUtils.buildAuthFromResultSet(rs, col, true);
                    sqlExtension.extendAuth(auth, rs.getInt(col.ID), con);
                    auths.add(auth);
                }
            }
        } catch (SQLException ex) {
            logSqlException(ex);
        }
        return auths;
    }

    @Override
    public List<String> getLoggedPlayersWithEmptyMail() {
        try (Connection con = getConnection()) {
            return findLoggedWithEmptyMail(con, findLoggedWithEmptyEmailSql);
        } catch (SQLException ex) {
            logSqlException(ex);
        }
        return new ArrayList<>();
    }

    @Override
    public List<PlayerAuth> getRecentlyLoggedInPlayers() {
        List<PlayerAuth> players = new ArrayList<>();
        try (Connection con = getConnection();
             PreparedStatement pst = con.prepareStatement(findRecentLoginsSql);
             ResultSet rs = pst.executeQuery()) {
            while (rs.next()) {
                players.add(SqlDataSourceUtils.buildAuthFromResultSet(rs, col, true));
            }
        } catch (SQLException e) {
            logSqlException(e);
        }
        return players;
    }

    @Override
    public void purgeRecords(Collection<String> toPurge) {
        try (Connection con = getConnection()) {
            deleteRecords(con, deleteByNameSql, toPurge);
        } catch (SQLException ex) {
            logSqlException(ex);
        }
    }

    @Override
    public boolean setTotpKey(String user, String totpKey) {
        try (Connection con = getConnection()) {
            return updateTotpKey(con, updateTotpSql, user, totpKey);
        } catch (SQLException e) {
            logSqlException(e);
        }
        return false;
    }
}
