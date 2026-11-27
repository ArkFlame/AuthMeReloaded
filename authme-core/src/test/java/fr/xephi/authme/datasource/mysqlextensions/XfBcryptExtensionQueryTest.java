package fr.xephi.authme.datasource.mysqlextensions;

import fr.xephi.authme.TestHelper;
import fr.xephi.authme.datasource.Columns;
import fr.xephi.authme.settings.Settings;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The xf_user_authenticate table is keyed by user_id, whatever the id column of the main table is called.
 */
class XfBcryptExtensionQueryTest {

    @Test
    void shouldFilterTheAuthenticateTableByUserId() throws Exception {
        // given
        Settings settings = mock(Settings.class);
        TestHelper.returnDefaultsForAllProperties(settings);
        XfBcryptExtension extension = new XfBcryptExtension(settings, new Columns(settings));
        Connection con = mock(Connection.class);
        PreparedStatement pst = mock(PreparedStatement.class);
        when(con.prepareStatement(anyString())).thenReturn(pst);
        when(pst.executeQuery()).thenReturn(mock(ResultSet.class));

        // when
        extension.extendAuth(null, 5, con);

        // then
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(con).prepareStatement(sql.capture());
        assertThat(sql.getValue(), containsString("user_authenticate WHERE user_id=?"));
    }
}
