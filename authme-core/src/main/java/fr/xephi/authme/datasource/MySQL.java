package fr.xephi.authme.datasource;

import com.google.common.annotations.VisibleForTesting;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import fr.xephi.authme.ConsoleLogger;
import fr.xephi.authme.datasource.mysqlextensions.MySqlExtensionsFactory;
import fr.xephi.authme.output.ConsoleLoggerFactory;
import fr.xephi.authme.settings.Settings;
import fr.xephi.authme.settings.properties.DatabaseSettings;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
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

    public MySQL(Settings settings, MySqlExtensionsFactory extensionsFactory) throws SQLException {
        setParameters(settings, extensionsFactory);

        // Set the connection arguments (and check if connection is ok)
        setupPool(this::setConnectionArguments, logger);

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
        setCommonParameters(settings, extensionsFactory);
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
        ds.setMaximumPoolSize(getPoolSize());
        ds.setMaxLifetime(getMaxLifetime() * 1000L);

        // Database URL
        ds.setJdbcUrl(this.getJdbcUrl(getHost(), getPort(), getDatabase()));

        // Auth
        ds.setUsername(getUsername());
        ds.setPassword(getPassword());
        // Driver
        String driverClassName = getDriverClassName();
        ds.setDriverClassName(driverClassName);

        // SSL mode: DISABLED / PREFERRED (SSL but no cert check) / VERIFY_CA
        // Migrated from deprecated useSSL + verifyServerCertificate (removed in Connector/J 9.x)
        String sslMode = !useSsl ? "DISABLED" : (!serverCertificateVerification ? "PREFERRED" : "VERIFY_CA");
        ds.addDataSourceProperty("sslMode", sslMode);
        // Ported from HaHaWTH/AuthMeReReloaded
        applyMariaDbSslMode(ds, driverClassName, mariaDbSslMode);

        if (allowPublicKeyRetrieval) {
            ds.addDataSourceProperty("allowPublicKeyRetrieval", "true");
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
        reloadPool(this::setConnectionArguments, logger);
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
            addColumnIfAbsent(md, st, col.NAME, "VARCHAR(255) NOT NULL UNIQUE AFTER " + col.ID + ";");
            addColumnIfAbsent(md, st, col.REAL_NAME, "VARCHAR(255) NOT NULL AFTER " + col.NAME + ";");
            addColumnIfAbsent(md, st, col.PASSWORD, "VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL;");

            if (!col.SALT.isEmpty()) {
                addColumnIfAbsent(md, st, col.SALT, "VARCHAR(255);");
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

            addColumnIfAbsent(md, st, col.REGISTRATION_IP, "VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin;");

            if (isColumnMissing(md, col.LASTLOC_X)) {
                alterTable(st, "ADD COLUMN " + col.LASTLOC_X + " DOUBLE NOT NULL DEFAULT '0.0' AFTER " + col.LAST_LOGIN
                    + " , ADD " + col.LASTLOC_Y + " DOUBLE NOT NULL DEFAULT '0.0' AFTER " + col.LASTLOC_X + " , ADD "
                    + col.LASTLOC_Z + " DOUBLE NOT NULL DEFAULT '0.0' AFTER " + col.LASTLOC_Y);
            } else {
                alterTable(st, "MODIFY " + col.LASTLOC_X + " DOUBLE NOT NULL DEFAULT '0.0', MODIFY "
                    + col.LASTLOC_Y + " DOUBLE NOT NULL DEFAULT '0.0', MODIFY "
                    + col.LASTLOC_Z + " DOUBLE NOT NULL DEFAULT '0.0';");
            }

            addColumnIfAbsent(md, st, col.LASTLOC_WORLD, "VARCHAR(255) NOT NULL DEFAULT 'world' AFTER " + col.LASTLOC_Z);
            addColumnIfAbsent(md, st, col.LASTLOC_YAW, "FLOAT;");
            addColumnIfAbsent(md, st, col.LASTLOC_PITCH, "FLOAT;");
            addColumnIfAbsent(md, st, col.EMAIL, "VARCHAR(255);");
            addColumnIfAbsent(md, st, col.IS_LOGGED, "SMALLINT NOT NULL DEFAULT '0' AFTER " + col.EMAIL);
            addColumnIfAbsent(md, st, col.HAS_SESSION, "SMALLINT NOT NULL DEFAULT '0' AFTER " + col.IS_LOGGED);

            if (isColumnMissing(md, col.TOTP_KEY)) {
                addColumn(st, col.TOTP_KEY, "VARCHAR(32);");
            } else if (SqlDataSourceUtils.getColumnSize(md, tableName, col.TOTP_KEY) != 32) {
                alterTable(st, "MODIFY " + col.TOTP_KEY + " VARCHAR(32);");
            }

            for (String uuidColumn : Arrays.asList(col.PLAYER_UUID, col.PREMIUM_UUID)) {
                if (!uuidColumn.isEmpty()) {
                    addColumnIfAbsent(md, st, uuidColumn, "VARCHAR(36)");
                }
            }
        }
        logger.info("MySQL setup finished");
    }

    @Override
    protected boolean isColumnMissing(DatabaseMetaData metaData, String columnName) throws SQLException {
        return SqlDataSourceUtils.isColumnMissing(metaData, getDatabase(), tableName, columnName);
    }

    @Override
    public DataSourceType getType() {
        return DataSourceType.MYSQL;
    }
}
