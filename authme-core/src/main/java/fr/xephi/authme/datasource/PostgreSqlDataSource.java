package fr.xephi.authme.datasource;

import com.google.common.annotations.VisibleForTesting;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.pool.HikariPool.PoolInitializationException;
import fr.xephi.authme.ConsoleLogger;
import fr.xephi.authme.datasource.columnshandler.AuthMeColumnsHandler;
import fr.xephi.authme.datasource.mysqlextensions.MySqlExtensionsFactory;
import fr.xephi.authme.output.ConsoleLoggerFactory;
import fr.xephi.authme.settings.Settings;
import fr.xephi.authme.settings.properties.DatabaseSettings;
import fr.xephi.authme.settings.properties.HooksSettings;

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

    private String host;
    private String port;
    private String username;
    private String password;
    private String database;
    private int poolSize;
    private int maxLifetime;

    public PostgreSqlDataSource(Settings settings, MySqlExtensionsFactory extensionsFactory) throws SQLException {
        setParameters(settings, extensionsFactory);

        // Set the connection arguments (and check if connection is ok)
        try {
            this.setConnectionArguments();
        } catch (RuntimeException e) {
            if (e instanceof IllegalArgumentException) {
                logger.warning("Invalid database arguments! Please check your configuration!");
                logger.warning("If this error persists, please report it to the developer!");
            }
            if (e instanceof PoolInitializationException) {
                logger.warning("Can't initialize database connection! Please check your configuration!");
                logger.warning("If this error persists, please report it to the developer!");
            }
            logger.warning("Can't use the Hikari Connection Pool! Please, report this error to the developer!");
            throw e;
        }

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
        setParameters(settings, extensionsFactory);
    }

    /**
     * Retrieves various settings.
     *
     * @param settings          the settings to read properties from
     * @param extensionsFactory factory to create the MySQL extension
     */
    private void setParameters(Settings settings, MySqlExtensionsFactory extensionsFactory) {
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
        initSqlStrings();
    }

    /**
     * Sets up the connection arguments to the database.
     */
    private void setConnectionArguments() {
        ds = new HikariDataSource();
        ds.setPoolName("AuthMePostgreSQLPool");

        // Pool Settings
        ds.setMaximumPoolSize(poolSize);
        ds.setMaxLifetime(maxLifetime * 1000);

        // Database URL
        ds.setDriverClassName("org.postgresql.Driver");
        ds.setJdbcUrl(this.getJdbcUrl(this.host, this.port, this.database));

        // Auth
        ds.setUsername(this.username);
        ds.setPassword(this.password);

        // Random stuff
        ds.addDataSourceProperty("reWriteBatchedInserts", "true");

        // Caching
        ds.addDataSourceProperty("cachePrepStmts", "true");
        ds.addDataSourceProperty("preparedStatementCacheQueries", "275");

        logger.info("Connection arguments loaded, Hikari ConnectionPool ready!");
    }

    @Override
    public void reload() {
        if (ds != null) {
            ds.close();
        }
        setConnectionArguments();
        logger.info("Hikari ConnectionPool arguments reloaded!");
    }

    /**
     * Creates the table or any of its required columns if they don't exist.
     */
    private void checkTablesAndColumns() throws SQLException {
        try (Connection con = getConnection(); Statement st = con.createStatement()) {
            // Create table with ID column if it doesn't exist
            String sql = "CREATE TABLE IF NOT EXISTS " + tableName + " ("
                + col.ID + " BIGSERIAL,"
                + "PRIMARY KEY (" + col.ID + ")"
                + ");";
            st.executeUpdate(sql);

            DatabaseMetaData md = con.getMetaData();
            addColumnIfAbsent(md, st, col.NAME, "VARCHAR(255) NOT NULL UNIQUE;");
            addColumnIfAbsent(md, st, col.REAL_NAME, "VARCHAR(255) NOT NULL;");
            addColumnIfAbsent(md, st, col.PASSWORD, "VARCHAR(255) NOT NULL;");

            if (!col.SALT.isEmpty() && isColumnMissing(md, col.SALT)) {
                addColumn(st, col.SALT, "VARCHAR(255);");
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

            if (!col.PLAYER_UUID.isEmpty() && isColumnMissing(md, col.PLAYER_UUID)) {
                addColumn(st, col.PLAYER_UUID, "VARCHAR(36)");
            }

            if (!col.PREMIUM_UUID.isEmpty() && isColumnMissing(md, col.PREMIUM_UUID)) {
                addColumn(st, col.PREMIUM_UUID, "VARCHAR(36)");
            }
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
