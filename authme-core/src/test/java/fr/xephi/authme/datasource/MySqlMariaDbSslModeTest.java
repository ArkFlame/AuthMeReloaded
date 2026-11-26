package fr.xephi.authme.datasource;

import com.zaxxer.hikari.HikariConfig;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

/**
 * Test for {@link MySQL#applyMariaDbSslMode}.
 */
class MySqlMariaDbSslModeTest {

    private static final String MARIA_DB_DRIVER = "org.mariadb.jdbc.Driver";

    @Test
    void shouldNotSetSslModeForEmptyValue() {
        HikariConfig config = new HikariConfig();

        MySQL.applyMariaDbSslMode(config, MARIA_DB_DRIVER, "");

        assertThat(config.getDataSourceProperties().getProperty("sslMode"), nullValue());
    }

    @Test
    void shouldSetSslModeForMariaDb() {
        HikariConfig config = new HikariConfig();

        MySQL.applyMariaDbSslMode(config, MARIA_DB_DRIVER, "verify-full");

        assertThat(config.getDataSourceProperties().getProperty("sslMode"), equalTo("verify-full"));
    }
}
