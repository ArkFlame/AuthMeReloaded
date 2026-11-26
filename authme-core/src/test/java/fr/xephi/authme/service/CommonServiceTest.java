package fr.xephi.authme.service;

import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.message.Messages;
import fr.xephi.authme.permission.PermissionNode;
import fr.xephi.authme.permission.PermissionsManager;
import fr.xephi.authme.permission.PlayerPermission;
import fr.xephi.authme.settings.Settings;
import fr.xephi.authme.settings.properties.SecuritySettings;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Test for {@link CommonService}.
 */
@ExtendWith(MockitoExtension.class)
class CommonServiceTest {

    private static final MessageKey TEST_KEY = MessageKey.ACCOUNT_NOT_ACTIVATED;

    @InjectMocks
    private CommonService commonService;

    @Mock
    private Settings settings;

    @Mock
    private Messages messages;

    @Mock
    private PermissionsManager permissionsManager;

    @Test
    void shouldGetProperty() {
        // given
        given(settings.getProperty(SecuritySettings.CAPTCHA_LENGTH)).willReturn(8);

        // when
        int result = commonService.getProperty(SecuritySettings.CAPTCHA_LENGTH);

        // then
        verify(settings).getProperty(SecuritySettings.CAPTCHA_LENGTH);
        assertThat(result, equalTo(8));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldSendMessage(boolean withReplacements) {
        // given
        CommandSender sender = mock(CommandSender.class);
        String[] replacements = {"test", "toast"};

        // when
        if (withReplacements) {
            commonService.send(sender, TEST_KEY, replacements);
        } else {
            commonService.send(sender, TEST_KEY);
        }

        // then
        if (withReplacements) {
            verify(messages).send(sender, TEST_KEY, replacements);
        } else {
            verify(messages).send(sender, TEST_KEY);
        }
    }

    @Test
    void shouldRetrieveSingleMessage() {
        // given
        CommandSender sender = mock(CommandSender.class);
        String text = "Test text";
        given(messages.retrieveSingle(sender, TEST_KEY)).willReturn(text);

        // when
        String result = commonService.retrieveSingleMessage(sender, TEST_KEY);

        // then
        assertThat(result, equalTo(text));
        verify(messages).retrieveSingle(sender, TEST_KEY);
    }

    @Test
    void shouldCheckPermission() {
        // given
        Player player = mock(Player.class);
        PermissionNode permission = PlayerPermission.CHANGE_PASSWORD;
        given(permissionsManager.hasPermission(player, permission)).willReturn(true);

        // when
        boolean result = commonService.hasPermission(player, permission);

        // then
        verify(permissionsManager).hasPermission(player, permission);
        assertThat(result, equalTo(true));
    }
}
