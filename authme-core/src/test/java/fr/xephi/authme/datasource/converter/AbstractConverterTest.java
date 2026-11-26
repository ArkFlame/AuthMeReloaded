package fr.xephi.authme.datasource.converter;

import fr.xephi.authme.TestHelper;
import org.bukkit.OfflinePlayer;
import org.junit.jupiter.api.BeforeAll;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * Base class for converter tests that need the logger to be initialized.
 */
abstract class AbstractConverterTest {

    @BeforeAll
    static void initLogger() {
        TestHelper.setupLogger();
    }

    /** Creates an offline player whose name is used by the converters to derive the account name. */
    static OfflinePlayer mockOfflinePlayer(String name) {
        OfflinePlayer player = mock(OfflinePlayer.class);
        given(player.getName()).willReturn(name);
        return player;
    }
}
