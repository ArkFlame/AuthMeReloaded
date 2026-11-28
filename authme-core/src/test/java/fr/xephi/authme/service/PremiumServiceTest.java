package fr.xephi.authme.service;

import fr.xephi.authme.TestHelper;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.data.auth.PlayerCache;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.message.Messages;
import fr.xephi.authme.service.bungeecord.BungeeSender;
import fr.xephi.authme.settings.Settings;
import fr.xephi.authme.settings.properties.PremiumSettings;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link PremiumService}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PremiumServiceTest {

    @InjectMocks
    private PremiumService service;

    @Mock
    private Settings settings;
    @Mock
    private DataSource dataSource;
    @Mock
    private PlayerCache playerCache;
    @Mock
    private Messages messages;
    @Mock
    private BukkitService bukkitService;
    @Mock
    private BungeeSender bungeeSender;
    @Mock
    private CommandSender sender;

    @BeforeAll
    static void initLogger() {
        TestHelper.setupLogger();
    }

    @BeforeEach
    void enableFeatureAndRunTasksInline() {
        given(settings.getProperty(PremiumSettings.ENABLE_PREMIUM)).willReturn(true);
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(bukkitService).runTaskOptionallyAsync(any(Runnable.class));
    }

    @Test
    void shouldKeepPremiumUuidWhenAdminDisableFailsToSave() {
        // given
        UUID premiumUuid = UUID.randomUUID();
        PlayerAuth auth = PlayerAuth.builder().name("bobby").premiumUuid(premiumUuid).build();
        given(dataSource.getAuth("bobby")).willReturn(auth);
        given(dataSource.updatePremiumUuid(auth)).willReturn(false);

        // when
        service.disablePremiumAdmin(sender, "bobby");

        // then
        assertEquals(premiumUuid, auth.getPremiumUuid());
        verify(messages).send(sender, MessageKey.PREMIUM_ERROR);
        verify(bungeeSender, never()).sendPremiumUnset("bobby");
    }

    @Test
    void shouldClearPremiumUuidWhenAdminDisableSucceeds() {
        // given
        PlayerAuth auth = PlayerAuth.builder().name("bobby").premiumUuid(UUID.randomUUID()).build();
        given(dataSource.getAuth("bobby")).willReturn(auth);
        given(dataSource.updatePremiumUuid(auth)).willReturn(true);

        // when
        service.disablePremiumAdmin(sender, "bobby");

        // then
        assertEquals(null, auth.getPremiumUuid());
        verify(bungeeSender).sendPremiumUnset("bobby");
        verify(playerCache).updatePlayerIfPresent(auth);
    }
}
