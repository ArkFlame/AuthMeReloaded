package fr.xephi.authme.datasource;

import com.zaxxer.hikari.HikariDataSource;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.datasource.mysqlextensions.MySqlExtension;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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

    private String findAuthSql;
    private String findRecordsToPurgeSql;
    private String updateOtherUsernamesSql;

    @Override
    protected void initSqlStrings() {
        super.initSqlStrings();
        findAuthSql = "SELECT * FROM " + tableName + " WHERE " + col.NAME + "=?;";
        findRecordsToPurgeSql = "SELECT " + col.NAME + " FROM " + tableName + " WHERE GREATEST("
            + " COALESCE(" + col.LAST_LOGIN + ", 0),"
            + " COALESCE(" + col.REGISTRATION_DATE + ", 0)"
            + ") < ?;";
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

    @Override
    public PlayerAuth getAuth(String user) {
        try (Connection con = getConnection(); PreparedStatement pst = con.prepareStatement(findAuthSql)) {
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
        super.saveAuth(auth);

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
        Set<String> list = new HashSet<>();
        try (Connection con = getConnection();
             PreparedStatement selectPst = con.prepareStatement(findRecordsToPurgeSql)) {
            selectPst.setLong(1, until);
            try (ResultSet rs = selectPst.executeQuery()) {
                while (rs.next()) {
                    list.add(rs.getString(col.NAME));
                }
            }
        } catch (SQLException ex) {
            logSqlException(ex);
        }
        return list;
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
        try (Connection con = getConnection(); Statement st = con.createStatement()) {
            try (ResultSet rs = st.executeQuery(findAllSql)) {
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
        List<String> players = new ArrayList<>();
        try (Connection con = getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(findLoggedWithEmptyEmailSql)) {
            while (rs.next()) {
                players.add(rs.getString(1));
            }
        } catch (SQLException ex) {
            logSqlException(ex);
        }
        return players;
    }

    @Override
    public List<PlayerAuth> getRecentlyLoggedInPlayers() {
        List<PlayerAuth> players = new ArrayList<>();
        try (Connection con = getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(findRecentLoginsSql)) {
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
        try (Connection con = getConnection(); PreparedStatement pst = con.prepareStatement(deleteByNameSql)) {
            for (String name : toPurge) {
                pst.setString(1, name.toLowerCase(Locale.ROOT));
                pst.addBatch();
            }
            pst.executeBatch();
        } catch (SQLException ex) {
            logSqlException(ex);
        }
    }

    @Override
    public boolean setTotpKey(String user, String totpKey) {
        try (Connection con = getConnection(); PreparedStatement pst = con.prepareStatement(updateTotpSql)) {
            pst.setString(1, totpKey);
            pst.setString(2, user.toLowerCase(Locale.ROOT));
            pst.executeUpdate();
            return true;
        } catch (SQLException e) {
            logSqlException(e);
        }
        return false;
    }
}
