package fr.xephi.authme.datasource.converter;

import fr.xephi.authme.datasource.DataSource;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * Verifies that the table name read from nLogin's config cannot be injected into the SQL query.
 */
class NLoginConverterQueryTest {

    private static final String TABLE_KEY = "database.table.account.table-name";

    private final NLoginConverter converter = new NLoginConverter(mock(DataSource.class));

    @ParameterizedTest
    @CsvSource({
        "nlogin_accounts, nlogin_accounts",
        "nlogin; DROP TABLE authme; --, nlogin"
    })
    void shouldOnlyUseSafeTableName(String configuredTable, String expectedTable) {
        YamlConfiguration config = mock(YamlConfiguration.class);
        given(config.getString(TABLE_KEY, "nlogin")).willReturn(configuredTable);

        String query = converter.getQuery(config);
        assertThat(query, containsString("FROM " + expectedTable));
        assertThat(query, not(containsString("DROP TABLE")));
        assertThat(query, not(containsString(";")));
    }
}
