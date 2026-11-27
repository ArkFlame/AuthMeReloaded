package fr.xephi.authme.process.email;

import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.data.auth.PlayerCache;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.service.CommonService;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    @Test
    void shouldKeepNewEmailAndRefreshCacheWhenSaved() {
        PlayerAuth auth = PlayerAuth.builder().name("bobby").email("old@example.org").build();
        given(dataSource.updateEmail(auth)).willReturn(true);

        boolean result = EmailProcessHelper.persistEmail(service, dataSource, playerCache, auth, player,
            "new@example.org", MessageKey.EMAIL_CHANGED_SUCCESS);

        assertThat(result, equalTo(true));
        assertThat(auth.getEmail(), equalTo("new@example.org"));
        verify(playerCache).updatePlayerIfPresent(auth);
        verify(service).send(player, MessageKey.EMAIL_CHANGED_SUCCESS);
    }

    @Test
    void shouldRestorePreviousEmailWhenTheSaveFails() {
        // The auth object is normally the very instance held by the player cache
        PlayerAuth auth = PlayerAuth.builder().name("bobby").email("old@example.org").build();
        given(dataSource.updateEmail(auth)).willReturn(false);

        boolean result = EmailProcessHelper.persistEmail(service, dataSource, playerCache, auth, player,
            "new@example.org", MessageKey.EMAIL_CHANGED_SUCCESS);

        assertThat(result, equalTo(false));
        assertThat(auth.getEmail(), equalTo("old@example.org"));
        verify(service).send(player, MessageKey.ERROR);
        verifyNoInteractions(playerCache);
    }
}
