package fr.xephi.authme.command.executable.totp;

import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import fr.xephi.authme.TestHelper;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.data.auth.PlayerCache;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.message.Messages;
import fr.xephi.authme.security.totp.TotpAuthenticator;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static java.util.Collections.singletonList;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static fr.xephi.authme.listener.TestPlayerUtils.mockPlayerWithName;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Test for {@link RemoveTotpCommand}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.WARN)
public class RemoveTotpCommandTest {

    @InjectMocks
    private RemoveTotpCommand command;

    @Mock
    private DataSource dataSource;
    @Mock
    private PlayerCache playerCache;
    @Mock
    private TotpAuthenticator totpAuthenticator;
    @Mock
    private Messages messages;
    @Mock
    private fr.xephi.authme.service.BukkitService bukkitService;

    @org.junit.jupiter.api.BeforeEach
    public void runTasksImmediately() {
        fr.xephi.authme.service.BukkitServiceTestHelper.setBukkitServiceToRunTaskOptionallyAsync(bukkitService);
    }

    @BeforeAll
    public static void setUpLogger() {
        TestHelper.setupLogger();
    }

    @Test
    public void shouldRemoveTotpKey() {
        // given
        String name = "aws";
        PlayerAuth auth = mockAuth(name, "some-totp-key");
        String inputCode = "93847";
        given(totpAuthenticator.checkCode(auth, inputCode)).willReturn(true);
        given(dataSource.removeTotpKey(name)).willReturn(true);
        Player player = mockPlayerWithName(name);

        // when
        command.runCommand(player, singletonList(inputCode));

        // then
        verify(dataSource).removeTotpKey(name);
        verify(messages, only()).send(player, MessageKey.TWO_FACTOR_REMOVED_SUCCESS);
        verify(playerCache).updatePlayerIfPresent(auth);
        assertThat(auth.getTotpKey(), nullValue());
    }

    @Test
    public void shouldHandleDatabaseError() {
        // given
        String name = "aws";
        PlayerAuth auth = mockAuth(name, "some-totp-key");
        String inputCode = "93847";
        given(totpAuthenticator.checkCode(auth, inputCode)).willReturn(true);
        given(dataSource.removeTotpKey(name)).willReturn(false);
        Player player = mockPlayerWithName(name);

        // when
        command.runCommand(player, singletonList(inputCode));

        // then
        verify(dataSource).removeTotpKey(name);
        verify(messages, only()).send(player, MessageKey.ERROR);
        verify(playerCache, only()).getAuth(name);
    }

    @Test
    public void shouldHandleInvalidCode() {
        // given
        String name = "cesar";
        PlayerAuth auth = mockAuth(name, "some-totp-key");
        String inputCode = "93847";
        given(totpAuthenticator.checkCode(auth, inputCode)).willReturn(false);
        Player player = mockPlayerWithName(name);

        // when
        command.runCommand(player, singletonList(inputCode));

        // then
        verifyNoInteractions(dataSource);
        verify(messages, only()).send(player, MessageKey.TWO_FACTOR_INVALID_CODE);
        verify(playerCache, only()).getAuth(name);
    }

    @Test
    public void shouldHandleUserWithoutTotpKey() {
        // given
        String name = "cesar";
        PlayerAuth auth = mockAuth(name, null);
        Player player = mockPlayerWithName(name);

        // when
        command.runCommand(player, singletonList("654684"));

        // then
        verifyNoInteractions(dataSource, totpAuthenticator);
        verify(messages, only()).send(player, MessageKey.TWO_FACTOR_NOT_ENABLED_ERROR);
        verify(playerCache, only()).getAuth(name);
    }

    @Test
    public void shouldHandleNonLoggedInUser() {
        // given
        String name = "cesar";
        given(playerCache.getAuth(name)).willReturn(null);
        Player player = mockPlayerWithName(name);

        // when
        command.runCommand(player, singletonList("654684"));

        // then
        verifyNoInteractions(dataSource, totpAuthenticator);
        verify(messages, only()).send(player, MessageKey.NOT_LOGGED_IN);
        verify(playerCache, only()).getAuth(name);
    }

    private PlayerAuth mockAuth(String name, String totpKey) {
        PlayerAuth auth = PlayerAuth.builder().name(name).totpKey(totpKey).build();
        given(playerCache.getAuth(name)).willReturn(auth);
        return auth;
    }
}
