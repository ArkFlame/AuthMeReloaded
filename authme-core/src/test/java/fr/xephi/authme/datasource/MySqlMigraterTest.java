package fr.xephi.authme.datasource;

import fr.xephi.authme.TestHelper;
import fr.xephi.authme.settings.Settings;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MySqlMigraterTest {

    @Test
    void shouldWriteTheRegistrationTimestampWithAsciiDigitsInAnyDefaultLocale() throws SQLException {
        TestHelper.setupLogger();
        Locale previous = Locale.getDefault();
        Locale.setDefault(new Locale("ar", "SA", "u-nu-arab"));
        try {
            Statement st = mock(Statement.class);

            MySqlMigrater.addRegistrationDateColumn(st, "authme", new Columns(mock(Settings.class)));

            ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
            verify(st, atLeastOnce()).executeUpdate(sql.capture());
            assertThat(sql.getAllValues().get(1), matchesPattern("UPDATE authme SET \\w+ = [0-9]+;"));
        } finally {
            Locale.setDefault(previous);
        }
    }
}
