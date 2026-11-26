package fr.xephi.authme.process.email;

import fr.xephi.authme.TestHelper;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.data.auth.PlayerCache;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.service.CommonService;
import fr.xephi.authme.service.PendingEmailVerificationCache;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Test for {@link AsyncConfirmEmail}.
 */
@ExtendWith(MockitoExtension.class)
class AsyncConfirmEmailTest {

    @InjectMocks
    private AsyncConfirmEmail asyncConfirmEmail;

    @Mock
    private Player player;
    @Mock
    private CommonService service;
    @Mock
    private PlayerCache playerCache;
    @Mock
    private DataSource dataSource;
    @Mock
    private PendingEmailVerificationCache pendingEmailVerificationCache;

    @BeforeEach
    void setUpLogger() {
        TestHelper.setupLogger();
    }

    private void givenMatchingPendingEntry(String code) {
        given(player.getName()).willReturn("TestEr");
        given(playerCache.isAuthenticated("tester")).willReturn(true);
        PendingEmailVerificationCache.PendingEntry entry =
            new PendingEmailVerificationCache.PendingEntry("new@mail.example", code);
        given(pendingEmailVerificationCache.getEntry("TestEr")).willReturn(entry);
    }

    @Test
    void shouldKeepPendingEntryWhenSaveFails() {
        givenMatchingPendingEntry("123456");
        PlayerAuth auth = mock(PlayerAuth.class);
        given(playerCache.getAuth("tester")).willReturn(auth);
        given(dataSource.updateEmail(auth)).willReturn(false);

        asyncConfirmEmail.confirmEmail(player, "123456");

        verify(dataSource).updateEmail(auth);
        verify(service).send(player, MessageKey.ERROR);
        verify(pendingEmailVerificationCache, never()).removePending("TestEr");
    }

    @Test
    void shouldConsumePendingEntryWhenSaveSucceeds() {
        givenMatchingPendingEntry("123456");
        PlayerAuth auth = mock(PlayerAuth.class);
        given(playerCache.getAuth("tester")).willReturn(auth);
        given(dataSource.updateEmail(auth)).willReturn(true);

        asyncConfirmEmail.confirmEmail(player, "123456");

        verify(dataSource).updateEmail(auth);
        verify(auth).setEmail("new@mail.example");
        verify(playerCache).updatePlayerIfPresent(auth);
        verify(service).send(player, MessageKey.EMAIL_CONFIRM_SUCCESS);
        verify(pendingEmailVerificationCache).removePending("TestEr");
    }
}
