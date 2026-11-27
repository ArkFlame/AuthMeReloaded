package fr.xephi.authme.process.email;

import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.data.auth.PlayerCache;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.service.CommonService;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Test for {@link EmailProcessHelper#persistEmail}.
 */
@ExtendWith(MockitoExtension.class)
class EmailProcessHelperTest {

    @Mock
    private CommonService service;
    @Mock
    private DataSource dataSource;
    @Mock
    private PlayerCache playerCache;
    @Mock
    private Player player;

    @ParameterizedTest(name = "save successful={0}")
    @CsvSource({"true, new@example.org, EMAIL_CHANGED_SUCCESS", "false, old@example.org, ERROR"})
    void shouldPersistEmail(boolean saveSuccessful, String expectedEmail, MessageKey expectedMessage) {
        PlayerAuth auth = PlayerAuth.builder().name("bobby").email("old@example.org").build();
        given(dataSource.updateEmail(auth)).willReturn(saveSuccessful);

        boolean result = EmailProcessHelper.persistEmail(service, dataSource, playerCache, auth, player,
            "new@example.org", MessageKey.EMAIL_CHANGED_SUCCESS);

        assertThat(result, equalTo(saveSuccessful));
        assertThat(auth.getEmail(), equalTo(expectedEmail));
        if (saveSuccessful) {
            verify(playerCache).updatePlayerIfPresent(auth);
        } else {
            verifyNoInteractions(playerCache);
        }
        verify(service).send(player, expectedMessage);
    }
}
