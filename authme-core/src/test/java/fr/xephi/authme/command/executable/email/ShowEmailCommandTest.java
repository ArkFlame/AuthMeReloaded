package fr.xephi.authme.command.executable.email;

import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.data.auth.PlayerCache;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.service.CommonService;
import fr.xephi.authme.settings.properties.SecuritySettings;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.Collections;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Test for {@link ShowEmailCommand}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.WARN)
public class ShowEmailCommandTest {

    private static final String CURRENT_EMAIL = "my.email@example.com";
    private static final String USERNAME = "name";

    @InjectMocks
    private ShowEmailCommand command;

    @Mock
    private CommonService commonService;

    @Mock
    private PlayerCache playerCache;

    @ParameterizedTest(name = "USE_EMAIL_MASKING={0}")
    @CsvSource({
        "false, my.email@example.com",
        "true, my.***@***mple.com"
    })
    public void shouldShowCurrentEmailMessage(boolean useEmailMasking, String expectedEmail) {
        // given
        Player sender = mock(Player.class);
        given(sender.getName()).willReturn(USERNAME);
        given(playerCache.getAuth(USERNAME)).willReturn(newAuthWithEmail(CURRENT_EMAIL));
        given(commonService.getProperty(SecuritySettings.USE_EMAIL_MASKING)).willReturn(useEmailMasking);

        // when
        command.executeCommand(sender, Collections.emptyList());

        // then
        verify(commonService).send(sender, MessageKey.EMAIL_SHOW, expectedEmail);
    }

    @ParameterizedTest(name = "malformed email {0} is masked to {1}")
    @CsvSource({
        "user@, us***@***",
        "@example.com, ***@***mple.com"
    })
    public void shouldMaskMalformedEmailWithoutThrowing(String email, String expectedEmail) {
        // given
        Player sender = mock(Player.class);
        given(sender.getName()).willReturn(USERNAME);
        given(playerCache.getAuth(USERNAME)).willReturn(newAuthWithEmail(email));
        given(commonService.getProperty(SecuritySettings.USE_EMAIL_MASKING)).willReturn(true);

        // when
        command.executeCommand(sender, Collections.emptyList());

        // then
        verify(commonService).send(sender, MessageKey.EMAIL_SHOW, expectedEmail);
    }

    @Test
    public void shouldReturnNoEmailMessage() {
        // given
        Player sender = mock(Player.class);
        given(sender.getName()).willReturn(USERNAME);
        given(playerCache.getAuth(USERNAME)).willReturn(newAuthWithNoEmail());

        // when
        command.executeCommand(sender, Collections.emptyList());

        // then
        verify(commonService).send(sender, MessageKey.SHOW_NO_EMAIL);
    }

    private static PlayerAuth newAuthWithEmail(String email) {
        return PlayerAuth.builder()
            .name(USERNAME)
            .email(email)
            .build();
    }

    private static PlayerAuth newAuthWithNoEmail() {
        return PlayerAuth.builder()
            .name(USERNAME)
            .build();
    }
}


