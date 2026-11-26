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
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Test for {@link ProxyLoginRequestValidator}.
 */
@ExtendWith(MockitoExtension.class)
class ProxyLoginRequestValidatorTest {

    @InjectMocks
    private ProxyLoginRequestValidator validator;

    @Mock
    private DataSource dataSource;

    @Mock
    private PlayerCache playerCache;

    @Mock
    private PendingPremiumCache pendingPremiumCache;

    @Mock
    private PremiumService premiumService;

    @Mock
    private BungeeSender bungeeSender;

    @Mock
    private Messages messages;

    @Mock
    private Settings settings;

    @Mock
    private Player player;

    @BeforeAll
    static void initLogger() {
        TestHelper.setupLogger();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldAcceptOrRejectStoredPremiumUuid(boolean matching) {
        UUID storedUuid = UUID.randomUUID();
        UUID forwardedUuid = matching ? storedUuid : UUID.randomUUID();
        PlayerAuth auth = PlayerAuth.builder().name("bobby").premiumUuid(storedUuid).build();
        givenPremiumPlayerWithAuth(auth);

        assertEquals(matching, validator.validate(player, forwardedUuid));
        verify(premiumService, never()).finalizePendingPremium(player, forwardedUuid);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldHandlePendingPremiumUuid(boolean matching) {
        UUID pendingUuid = UUID.randomUUID();
        UUID forwardedUuid = matching ? pendingUuid : UUID.randomUUID();
        PlayerAuth auth = PlayerAuth.builder().name("bobby").build();
        givenPremiumPlayerWithAuth(auth);
        given(pendingPremiumCache.removePending("Bobby")).willReturn(pendingUuid);

        assertEquals(matching, validator.validate(player, forwardedUuid));
        if (matching) {
            verify(premiumService).finalizePendingPremium(player, forwardedUuid);
        } else {
            verify(bungeeSender).sendPremiumUnset("Bobby");
            verify(messages).send(player, MessageKey.PREMIUM_PENDING_FAIL);
            verify(premiumService, never()).finalizePendingPremium(player, forwardedUuid);
        }
    }

    @Test
    void shouldStaySilentWhenPendingAlreadyConsumed() {
        // given — canBypassWithPremium already consumed the pending entry via the atomic gate;
        // the concurrent perform.login validate must not emit a spurious PREMIUM_PENDING_FAIL
        UUID forwardedUuid = UUID.randomUUID();
        PlayerAuth auth = PlayerAuth.builder().name("bobby").build();
        givenPremiumPlayerWithAuth(auth);
        given(pendingPremiumCache.removePending("Bobby")).willReturn(null);

        assertFalse(validator.validate(player, forwardedUuid));
        verify(bungeeSender, never()).sendPremiumUnset("Bobby");
        verify(messages, never()).send(player, MessageKey.PREMIUM_PENDING_FAIL);
        verify(premiumService, never()).finalizePendingPremium(player, forwardedUuid);
    }

    @Test
    void shouldRejectProxyPremiumClaimAndNotifyProxyWhenPremiumIsDisabled() {
        UUID forwardedUuid = UUID.randomUUID();
        given(player.getName()).willReturn("Bobby");
        given(settings.getProperty(PremiumSettings.ENABLE_PREMIUM)).willReturn(false);

        assertFalse(validator.validate(player, forwardedUuid));
        verify(bungeeSender).sendPremiumUnset("Bobby");
        verify(messages, never()).send(player, MessageKey.PREMIUM_PENDING_FAIL);
        verifyNoInteractions(playerCache, dataSource, pendingPremiumCache, premiumService);
    }

    private void givenPremiumPlayerWithAuth(PlayerAuth auth) {
        given(player.getName()).willReturn("Bobby");
        given(settings.getProperty(PremiumSettings.ENABLE_PREMIUM)).willReturn(true);
        given(playerCache.getAuth("Bobby")).willReturn(auth);
    }
}
