package fr.xephi.authme.datasource.converter;

import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.datasource.DataSource;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.sql.SQLException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Tests the import summary of {@link ConverterUtils#importRows}.
 */
class ConverterUtilsImportRowsTest extends AbstractConverterTest {

    @Test
    void shouldCountOnlyAccountsThatWereSaved() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        given(rs.next()).willReturn(true, true, false);
        DataSource dataSource = mock(DataSource.class);
        given(dataSource.saveAuth(any(PlayerAuth.class))).willReturn(true, false);
        CommandSender sender = mock(CommandSender.class);
        String[] names = {"first", "second"};
        int[] index = {0};

        ConverterUtils.importRows(rs, dataSource, sender, "Test",
            row -> names[index[0]++],
            (row, name) -> PlayerAuth.builder().name(name).build());

        verify(sender).sendMessage(contains("could not save account 'second'"));
        verify(sender).sendMessage(contains("1 account(s) imported"));
    }
}
