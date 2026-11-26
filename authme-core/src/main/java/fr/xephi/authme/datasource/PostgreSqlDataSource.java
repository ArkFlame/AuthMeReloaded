package fr.xephi.authme.datasource;

import com.google.common.annotations.VisibleForTesting;
import com.zaxxer.hikari.HikariDataSource;
import fr.xephi.authme.ConsoleLogger;
import fr.xephi.authme.datasource.mysqlextensions.MySqlExtensionsFactory;
import fr.xephi.authme.output.ConsoleLoggerFactory;
import fr.xephi.authme.settings.Settings;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

/**
 * PostgreSQL data source.
 */
public class PostgreSqlDataSource extends AbstractHikariSqlDataSource {

    private final ConsoleLogger logger = ConsoleLoggerFactory.get(PostgreSqlDataSource.class);

    public PostgreSqlDataSource(Settings settings, MySqlExtensionsFactory extensionsFactory) throws SQLException {
        setCommonParameters(settings, extensionsFactory);

        // Set the connection arguments (and check if connection is ok)
        setupPool(this::setConnectionArguments, logger);

        // Initialize the database
        try {
            checkTablesAndColumns();
        } catch (SQLException e) {
            closeConnection();
            logger.logException("Can't initialize the PostgreSQL database:", e);
            logger.warning("Please check your database settings in the config.yml file!");
            throw e;
        }
    }

    @VisibleForTesting
    PostgreSqlDataSource(Settings settings, HikariDataSource hikariDataSource,
                         MySqlExtensionsFactory extensionsFactory) {
        ds = hikariDataSource;
        setCommonParameters(settings, extensionsFactory);
    }

    /**
     * Sets up the connection arguments to the database.
     */
    private void setConnectionArguments() {
        ds = new HikariDataSource();
        ds.setPoolName("AuthMePostgreSQLPool");

        // Pool Settings
        ds.setMaximumPoolSize(getPoolSize());
        ds.setMaxLifetime(getMaxLifetime() * 1000L);

        // Database URL
        ds.setDriverClassName("org.postgresql.Driver");
        ds.setJdbcUrl(getJdbcUrl(getHost(), getPort(), getDatabase()));

        // Auth
        ds.setUsername(getUsername());
        ds.setPassword(getPassword());

        // Random stuff
        ds.addDataSourceProperty("reWriteBatchedInserts", "true");

        // Caching
        ds.addDataSourceProperty("cachePrepStmts", "true");
        ds.addDataSourceProperty("preparedStatementCacheQueries", "275");

        logger.info("Connection arguments loaded, Hikari ConnectionPool ready!");
    }

    @Override
    public void reload() {
        reloadPool(this::setConnectionArguments, logger);
    }

    /**
     * Creates the table or any of its required columns if they don't exist.
     */
    private void checkTablesAndColumns() throws SQLException {
        try (Connection con = getConnection(); Statement st = con.createStatement()) {
            // Create table with ID column if it doesn't exist
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + tableName + " ("
                + col.ID + " BIGSERIAL,"
                + "PRIMARY KEY (" + col.ID + ")"
                + ");");

            DatabaseMetaData md = con.getMetaData();
            addColumnIfAbsent(md, st, col.NAME, "VARCHAR(255) NOT NULL UNIQUE;");
            addColumnIfAbsent(md, st, col.REAL_NAME, "VARCHAR(255) NOT NULL;");
            addColumnIfAbsent(md, st, col.PASSWORD, "VARCHAR(255) NOT NULL;");

            if (!col.SALT.isEmpty()) {
                addColumnIfAbsent(md, st, col.SALT, "VARCHAR(255);");
            }

            if (isColumnMissing(md, col.LAST_IP)) {
                addColumn(st, col.LAST_IP, "VARCHAR(40);");
            } else {
                MySqlMigrater.migrateLastIpColumn(st, md, tableName, col);
            }

            if (isColumnMissing(md, col.LAST_LOGIN)) {
                addColumn(st, col.LAST_LOGIN, "BIGINT;");
            } else {
                MySqlMigrater.migrateLastLoginColumn(st, md, tableName, col);
            }

            if (isColumnMissing(md, col.REGISTRATION_DATE)) {
                MySqlMigrater.addRegistrationDateColumn(st, tableName, col);
            }

            addColumnIfAbsent(md, st, col.REGISTRATION_IP, "VARCHAR(40);");

            if (isColumnMissing(md, col.LASTLOC_X)) {
                alterTable(st, "ADD COLUMN " + col.LASTLOC_X + " DOUBLE PRECISION NOT NULL DEFAULT '0.0' , ADD "
                    + col.LASTLOC_Y + " DOUBLE PRECISION NOT NULL DEFAULT '0.0' , ADD "
                    + col.LASTLOC_Z + " DOUBLE PRECISION NOT NULL DEFAULT '0.0';");
            }

            addColumnIfAbsent(md, st, col.LASTLOC_WORLD, "VARCHAR(255) NOT NULL DEFAULT 'world';");
            addColumnIfAbsent(md, st, col.LASTLOC_YAW, "FLOAT;");
            addColumnIfAbsent(md, st, col.LASTLOC_PITCH, "FLOAT;");
            addColumnIfAbsent(md, st, col.EMAIL, "VARCHAR(255);");
            addColumnIfAbsent(md, st, col.IS_LOGGED, "SMALLINT NOT NULL DEFAULT '0';");
            addColumnIfAbsent(md, st, col.HAS_SESSION, "SMALLINT NOT NULL DEFAULT '0';");

            if (isColumnMissing(md, col.TOTP_KEY)) {
                addColumn(st, col.TOTP_KEY, "VARCHAR(32);");
            } else if (SqlDataSourceUtils.getColumnSize(md, tableName, col.TOTP_KEY) != 32) {
                alterTable(st, "ALTER COLUMN " + col.TOTP_KEY + " TYPE VARCHAR(32);");
            }

            addUuidColumnsIfAbsent(md, st);
        }
        logger.info("PostgreSQL setup finished");
    }

    @Override
    protected boolean isColumnMissing(DatabaseMetaData metaData, String columnName) throws SQLException {
        return SqlDataSourceUtils.isColumnMissing(metaData, null, tableName, columnName.toLowerCase(Locale.ROOT));
    }

    @Override
    String getJdbcUrl(String host, String port, String database) {
        return "jdbc:postgresql://" + host + ":" + port + "/" + database;
    }

    @Override
    public DataSourceType getType() {
        return DataSourceType.POSTGRESQL;
    }
}
