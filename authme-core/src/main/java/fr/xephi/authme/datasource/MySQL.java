package fr.xephi.authme.datasource;

import com.google.common.annotations.VisibleForTesting;
import com.zaxxer.hikari.HikariConfig;
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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

/**
 * MySQL data source.
 */
@SuppressWarnings({"checkstyle:AbbreviationAsWordInName"}) // Justification: Class name cannot be changed anymore
public class MySQL extends AbstractHikariSqlDataSource {
    private final ConsoleLogger logger = ConsoleLoggerFactory.get(MySQL.class);

    private boolean useSsl;
    private boolean serverCertificateVerification;
    private boolean allowPublicKeyRetrieval;
    private String mariaDbSslMode;
    private String host;
    private String port;
    private String username;
    private String password;
    private String database;
    private int poolSize;
    private int maxLifetime;

    public MySQL(Settings settings, MySqlExtensionsFactory extensionsFactory) throws SQLException {
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
            logger.logException("Can't initialize the MySQL database:", e);
            logger.warning("Please check your database settings in the config.yml file!");
            throw e;
        }
    }

    @VisibleForTesting
    MySQL(Settings settings, HikariDataSource hikariDataSource, MySqlExtensionsFactory extensionsFactory) {
        ds = hikariDataSource;
        setParameters(settings, extensionsFactory);
    }

    /**
     * Returns the path of the Driver class to use when connecting to the database.
     *
     * @return the dotted path of the SQL driver class to be used
     */
    protected String getDriverClassName() {
        return "com.mysql.cj.jdbc.Driver";
    }

    /**
     * Retrieves various settings.
     *
     * @param settings the settings to read properties from
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
        this.useSsl = settings.getProperty(DatabaseSettings.MYSQL_USE_SSL);
        this.serverCertificateVerification = settings.getProperty(DatabaseSettings.MYSQL_CHECK_SERVER_CERTIFICATE);
        this.allowPublicKeyRetrieval = settings.getProperty(DatabaseSettings.MYSQL_ALLOW_PUBLIC_KEY_RETRIEVAL);
        this.mariaDbSslMode = settings.getProperty(DatabaseSettings.MARIADB_SSL_MODE);
    }

    /**
     * Sets up the connection arguments to the database.
     */
    private void setConnectionArguments() {
        ds = new HikariDataSource();
        ds.setPoolName("AuthMeMYSQLPool");

        // Pool Settings
        ds.setMaximumPoolSize(poolSize);
        ds.setMaxLifetime(maxLifetime * 1000L);

        // Database URL
        ds.setJdbcUrl(this.getJdbcUrl(this.host, this.port, this.database));

        // Auth
        ds.setUsername(this.username);
        ds.setPassword(this.password);
        
        // Driver
        ds.setDriverClassName(this.getDriverClassName());

        // SSL mode: DISABLED / PREFERRED (SSL but no cert check) / VERIFY_CA
        // Migrated from deprecated useSSL + verifyServerCertificate (removed in Connector/J 9.x)
        String sslMode = !useSsl ? "DISABLED" : (!serverCertificateVerification ? "PREFERRED" : "VERIFY_CA");
        ds.addDataSourceProperty("sslMode", sslMode);
        // Ported from HaHaWTH/AuthMeReReloaded
        applyMariaDbSslMode(ds, this.getDriverClassName(), mariaDbSslMode);

        if (allowPublicKeyRetrieval) {
            ds.addDataSourceProperty("allowPublicKeyRetrieval", String.valueOf(true));
        }

        // Encoding
        ds.addDataSourceProperty("characterEncoding", "utf8");
        ds.addDataSourceProperty("encoding", "UTF-8");
        ds.addDataSourceProperty("useUnicode", "true");

        // Random stuff
        ds.addDataSourceProperty("rewriteBatchedStatements", "true");
        ds.addDataSourceProperty("jdbcCompliantTruncation", "false");

        // Caching
        ds.addDataSourceProperty("cachePrepStmts", "true");
        ds.addDataSourceProperty("prepStmtCacheSize", "275");
        ds.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        logger.info("Connection arguments loaded, Hikari ConnectionPool ready!");
    }

    /**
     * Overrides the SSL mode with the configured MariaDB value when a MariaDB driver is used.
     *
     * @param config the Hikari config to set the property on
     * @param driverClassName the driver class name used by the data source
     * @param value the configured MariaDB SSL mode, empty to leave the config unchanged
     */
    @VisibleForTesting
    static void applyMariaDbSslMode(HikariConfig config, String driverClassName, String value) {
        if (value == null || value.isEmpty() || driverClassName == null
            || !driverClassName.toLowerCase(Locale.ROOT).contains("mariadb")) {
            return;
        }
        config.addDataSourceProperty("sslMode", value);
    }

    @Override
    public void reload() {
        if (ds != null) {
            ds.close();
        }
        setConnectionArguments();
        logger.info("Hikari ConnectionPool arguments reloaded!");
    }

    @Override
    String getJdbcUrl(String host, String port, String database) {
        return "jdbc:mysql://" + host + ":" + port + "/" + database;
    }

    /**
     * Creates the table or any of its required columns if they don't exist.
     */
    @SuppressWarnings({"checkstyle:CyclomaticComplexity", "checkstyle:JavaNCSS"})
    private void checkTablesAndColumns() throws SQLException {
        try (Connection con = getConnection(); Statement st = con.createStatement()) {
            // Create table with ID column if it doesn't exist
            String sql = "CREATE TABLE IF NOT EXISTS " + tableName + " ("
                + col.ID + " MEDIUMINT(8) UNSIGNED AUTO_INCREMENT,"
                + "PRIMARY KEY (" + col.ID + ")"
                + ") CHARACTER SET = utf8;";
            st.executeUpdate(sql);

            DatabaseMetaData md = con.getMetaData();
            if (isColumnMissing(md, col.NAME)) {
                addColumn(st, col.NAME, "VARCHAR(255) NOT NULL UNIQUE AFTER " + col.ID + ";");
            }

            if (isColumnMissing(md, col.REAL_NAME)) {
                addColumn(st, col.REAL_NAME, "VARCHAR(255) NOT NULL AFTER " + col.NAME + ";");
            }

            if (isColumnMissing(md, col.PASSWORD)) {
                addColumn(st, col.PASSWORD, "VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL;");
            }

            if (!col.SALT.isEmpty() && isColumnMissing(md, col.SALT)) {
                addColumn(st, col.SALT, "VARCHAR(255);");
            }

            if (isColumnMissing(md, col.LAST_IP)) {
                addColumn(st, col.LAST_IP, "VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin;");
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

            if (isColumnMissing(md, col.REGISTRATION_IP)) {
                addColumn(st, col.REGISTRATION_IP, "VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin;");
            }

            if (isColumnMissing(md, col.LASTLOC_X)) {
                alterTable(st, "ADD COLUMN " + col.LASTLOC_X + " DOUBLE NOT NULL DEFAULT '0.0' AFTER " + col.LAST_LOGIN
                    + " , ADD " + col.LASTLOC_Y + " DOUBLE NOT NULL DEFAULT '0.0' AFTER " + col.LASTLOC_X + " , ADD "
                    + col.LASTLOC_Z + " DOUBLE NOT NULL DEFAULT '0.0' AFTER " + col.LASTLOC_Y);
            } else {
                alterTable(st, "MODIFY " + col.LASTLOC_X + " DOUBLE NOT NULL DEFAULT '0.0', MODIFY "
                    + col.LASTLOC_Y + " DOUBLE NOT NULL DEFAULT '0.0', MODIFY "
                    + col.LASTLOC_Z + " DOUBLE NOT NULL DEFAULT '0.0';");
            }

            if (isColumnMissing(md, col.LASTLOC_WORLD)) {
                addColumn(st, col.LASTLOC_WORLD, "VARCHAR(255) NOT NULL DEFAULT 'world' AFTER " + col.LASTLOC_Z);
            }

            if (isColumnMissing(md, col.LASTLOC_YAW)) {
                addColumn(st, col.LASTLOC_YAW, "FLOAT;");
            }

            if (isColumnMissing(md, col.LASTLOC_PITCH)) {
                addColumn(st, col.LASTLOC_PITCH, "FLOAT;");
            }

            if (isColumnMissing(md, col.EMAIL)) {
                addColumn(st, col.EMAIL, "VARCHAR(255);");
            }

            if (isColumnMissing(md, col.IS_LOGGED)) {
                addColumn(st, col.IS_LOGGED, "SMALLINT NOT NULL DEFAULT '0' AFTER " + col.EMAIL);
            }

            if (isColumnMissing(md, col.HAS_SESSION)) {
                addColumn(st, col.HAS_SESSION, "SMALLINT NOT NULL DEFAULT '0' AFTER " + col.IS_LOGGED);
            }

            if (isColumnMissing(md, col.TOTP_KEY)) {
                addColumn(st, col.TOTP_KEY, "VARCHAR(32);");
            } else if (SqlDataSourceUtils.getColumnSize(md, tableName, col.TOTP_KEY) != 32) {
                alterTable(st, "MODIFY " + col.TOTP_KEY + " VARCHAR(32);");
            }

            if (!col.PLAYER_UUID.isEmpty() && isColumnMissing(md, col.PLAYER_UUID)) {
                addColumn(st, col.PLAYER_UUID, "VARCHAR(36)");
            }

            if (!col.PREMIUM_UUID.isEmpty() && isColumnMissing(md, col.PREMIUM_UUID)) {
                addColumn(st, col.PREMIUM_UUID, "VARCHAR(36)");
            }
        }
        logger.info("MySQL setup finished");
    }

    private void addColumn(Statement st, String column, String definition) throws SQLException {
        alterTable(st, "ADD COLUMN " + column + " " + definition);
    }

    private void alterTable(Statement st, String alteration) throws SQLException {
        st.executeUpdate("ALTER TABLE " + tableName + " " + alteration);
    }

    private boolean isColumnMissing(DatabaseMetaData metaData, String columnName) throws SQLException {
        try (ResultSet rs = metaData.getColumns(database, null, tableName, columnName)) {
            return !rs.next();
        }
    }

    @Override
    public DataSourceType getType() {
        return DataSourceType.MYSQL;
    }
}
