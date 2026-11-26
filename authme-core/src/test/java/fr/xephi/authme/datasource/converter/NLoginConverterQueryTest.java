package fr.xephi.authme.datasource.converter;

import fr.xephi.authme.datasource.DataSource;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

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

    @Test
    void shouldUseSafeConfiguredTableName() {
        YamlConfiguration config = mock(YamlConfiguration.class);
        given(config.getString(TABLE_KEY, "nlogin")).willReturn("nlogin_accounts");

        assertThat(converter.getQuery(config), containsString("FROM nlogin_accounts"));
    }

    @Test
    void shouldFallBackToDefaultTableForInjectedIdentifier() {
        YamlConfiguration config = mock(YamlConfiguration.class);
        given(config.getString(TABLE_KEY, "nlogin")).willReturn("nlogin; DROP TABLE authme; --");

        String query = converter.getQuery(config);
        assertThat(query, containsString("FROM nlogin"));
        assertThat(query, not(containsString("DROP TABLE")));
        assertThat(query, not(containsString(";")));
    }
}
