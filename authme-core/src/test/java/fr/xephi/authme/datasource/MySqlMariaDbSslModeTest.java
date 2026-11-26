package fr.xephi.authme.datasource;

import com.zaxxer.hikari.HikariConfig;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Test for {@link MySQL#applyMariaDbSslMode}.
 */
class MySqlMariaDbSslModeTest {

    private static final String MARIA_DB_DRIVER = "org.mariadb.jdbc.Driver";

    @ParameterizedTest(name = "{0}")
    @MethodSource("sslModeCases")
    void shouldApplyMariaDbSslMode(String description, String sslMode, Matcher<String> expected) {
        HikariConfig config = new HikariConfig();

        MySQL.applyMariaDbSslMode(config, MARIA_DB_DRIVER, sslMode);

        assertThat(config.getDataSourceProperties().getProperty("sslMode"), expected);
    }

    private static Stream<Arguments> sslModeCases() {
        return Stream.of(
            Arguments.of("empty SSL mode is ignored", "", Matchers.<String>nullValue()),
            Arguments.of("MariaDB SSL mode is applied", "verify-full", Matchers.<String>equalTo("verify-full")));
    }
}
