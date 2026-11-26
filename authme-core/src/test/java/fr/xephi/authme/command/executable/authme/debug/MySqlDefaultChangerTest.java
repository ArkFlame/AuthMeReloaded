package fr.xephi.authme.command.executable.authme.debug;

import com.zaxxer.hikari.HikariDataSource;
import fr.xephi.authme.ReflectionTestUtils;
import fr.xephi.authme.TestHelper;
import fr.xephi.authme.data.auth.PlayerCache;
import fr.xephi.authme.datasource.CacheDataSource;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.datasource.MySQL;
import fr.xephi.authme.datasource.SqlDataSourceTestUtil;
import fr.xephi.authme.settings.Settings;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.Connection;
import java.sql.SQLException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Test for {@link MySqlDefaultChanger}.
 */
class MySqlDefaultChangerTest {

    @BeforeAll
    static void setUpLogger() {
        TestHelper.setupLogger();
    }

    @Test
    void shouldReturnMySqlConnection() throws SQLException {
        // given
        Settings settings = mock(Settings.class);
        TestHelper.returnDefaultsForAllProperties(settings);
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Connection connection = mock(Connection.class);
        given(dataSource.getConnection()).willReturn(connection);
        MySQL mySQL = SqlDataSourceTestUtil.createMySql(settings, dataSource);
        MySqlDefaultChanger defaultChanger = createDefaultChanger(mySQL);

        // when
        Connection result = defaultChanger.getConnection(mySQL);

        // then
        assertThat(result, equalTo(connection));
        verify(dataSource).getConnection();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldSetMySqlFieldOnInitialization(boolean mysqlSource) {
        // given
        DataSource dataSource = mysqlSource
            ? mock(MySQL.class)
            : new CacheDataSource(mock(DataSource.class), mock(PlayerCache.class));
        MySqlDefaultChanger defaultChanger = createDefaultChanger(dataSource);

        // when
        defaultChanger.setMySqlField();

        // then
        Object mySql = ReflectionTestUtils.getFieldValue(
            MySqlDefaultChanger.class, defaultChanger, "mySql");
        assertThat(mySql, mysqlSource ? sameInstance((Object) dataSource) : nullValue());
    }

    private MySqlDefaultChanger createDefaultChanger(DataSource dataSource) {
        MySqlDefaultChanger defaultChanger = new MySqlDefaultChanger();
        ReflectionTestUtils.setField(defaultChanger, "dataSource", dataSource);
        return defaultChanger;
    }
}
