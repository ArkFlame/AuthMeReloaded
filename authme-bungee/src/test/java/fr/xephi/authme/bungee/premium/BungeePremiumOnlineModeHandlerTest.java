package fr.xephi.authme.bungee.premium;

import net.md_5.bungee.api.connection.PendingConnection;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class BungeePremiumOnlineModeHandlerTest {

    @ParameterizedTest
    @CsvSource({
        "Alice, true",
        "Bob, false"
    })
    void shouldSetOnlineModeOnlyForPremiumPlayers(String name, boolean shouldEnable) {
        PendingConnection connection = mock(PendingConnection.class);
        given(connection.getName()).willReturn(name);
        given(connection.isOnlineMode()).willReturn(false);

        BungeePremiumOnlineModeHandler handler = new BungeePremiumOnlineModeHandler("alice"::equals);
        handler.enableOnlineModeIfRequired(connection);

        verify(connection, times(shouldEnable ? 1 : 0)).setOnlineMode(true);
    }
}
