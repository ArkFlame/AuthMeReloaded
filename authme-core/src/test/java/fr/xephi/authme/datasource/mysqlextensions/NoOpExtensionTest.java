package fr.xephi.authme.datasource.mysqlextensions;

import fr.xephi.authme.TestHelper;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.datasource.Columns;
import fr.xephi.authme.security.crypts.HashedPassword;
import fr.xephi.authme.settings.Settings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Connection;
import java.sql.SQLException;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Test for {@link NoOpExtension}.
 */
@ExtendWith(MockitoExtension.class)
class NoOpExtensionTest {

    private NoOpExtension extension;

    @BeforeEach
    void createExtension() {
        Settings settings = mock(Settings.class);
        TestHelper.returnDefaultsForAllProperties(settings);
        extension = new NoOpExtension(settings, new Columns(settings));
    }

    @Test
    void shouldNotHaveAnyInteractionsWithConnection() throws SQLException {
        Connection connection = mock(Connection.class);
        PlayerAuth auth = mock(PlayerAuth.class);

        extension.extendAuth(auth, 3, connection);
        extension.changePassword("Bobby", new HashedPassword("test", "toast"), connection);
        extension.removeAuth("Bobby", connection);
        extension.saveAuth(auth, connection);

        verifyNoInteractions(connection, auth);
    }
}
