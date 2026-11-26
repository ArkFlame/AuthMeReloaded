package fr.xephi.authme.service;

import fr.xephi.authme.TestHelper;
import fr.xephi.authme.data.ProxySessionManager;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.process.SyncProcessManager;
import fr.xephi.authme.process.login.AsynchronousLogin;
import fr.xephi.authme.security.PasswordSecurity;
import fr.xephi.authme.security.crypts.HashedPassword;
import fr.xephi.authme.service.bungeecord.BungeeSender;
import fr.xephi.authme.settings.Settings;
import fr.xephi.authme.settings.properties.PremiumSettings;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link PremiumAutoRegisterService}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PremiumAutoRegisterServiceTest {

    @InjectMocks
    private PremiumAutoRegisterService service;

    @Mock
    private Settings settings;
    @Mock
    private DataSource dataSource;
    @Mock
    private BukkitService bukkitService;
    @Mock
    private PremiumLoginVerifier premiumLoginVerifier;
    @Mock
    private ProxySessionManager proxySessionManager;
    @Mock
    private BungeeSender bungeeSender;
    @Mock
    private PasswordSecurity passwordSecurity;
    @Mock
    private AsynchronousLogin asynchronousLogin;
    @Mock
    private SyncProcessManager syncProcessManager;
    @Mock
    private Player player;

    @BeforeAll
    static void initLogger() {
        TestHelper.setupLogger();
    }

    @BeforeEach
    void setUp() {
        BukkitServiceTestHelper.setBukkitServiceToRunTaskOptionallyAsync(bukkitService);
        given(player.getUniqueId()).willReturn(UUID.randomUUID());
        given(player.getName()).willReturn("Bobby");
        given(player.getUniqueId()).willReturn(offlineUuid());
        TestHelper.mockIpAddressToPlayer(player, "127.0.0.1");
        given(settings.getProperty(PremiumSettings.AUTO_REGISTER)).willReturn(true);
        given(settings.getProperty(PremiumSettings.ENABLE_PREMIUM)).willReturn(true);
        given(dataSource.isAuthAvailable("bobby")).willReturn(false);
        given(dataSource.saveAuth(any(PlayerAuth.class))).willReturn(true);
        given(dataSource.updatePremiumUuid(any(PlayerAuth.class))).willReturn(true);
        given(passwordSecurity.computeHash(anyString(), eq("bobby")))
            .willReturn(new HashedPassword("hash", "salt"));
    }

    @Test
    void shouldRegisterVerifiedPremiumPlayerOnce() {
        UUID premiumUuid = UUID.randomUUID();
        given(player.getUniqueId()).willReturn(premiumUuid);

        assertTrue(service.tryAutoRegister(player));

        PlayerAuth auth = captureSavedAuth();
        assertEquals("bobby", auth.getNickname());
        assertEquals(premiumUuid, auth.getPremiumUuid());
        assertNotNull(auth.getPassword());
        verify(dataSource).updatePremiumUuid(auth);
        verify(bungeeSender).sendPremiumSet("Bobby");
        verify(asynchronousLogin).forceLogin(player);
        verify(syncProcessManager).processSyncPasswordRegister(player);
    }

    @Test
    void shouldRegisterPlayerVerifiedByPacketEvents() {
        UUID premiumUuid = UUID.randomUUID();
        given(premiumLoginVerifier.getVerifiedUuid("Bobby")).willReturn(premiumUuid);
        given(premiumLoginVerifier.consumeVerifiedUuid("Bobby")).willReturn(premiumUuid);

        assertTrue(service.tryAutoRegister(player));

        assertEquals(premiumUuid, captureSavedAuth().getPremiumUuid());
        verify(premiumLoginVerifier).consumeVerifiedUuid("Bobby");
    }

    @Test
    void shouldRegisterPlayerWithVerifiedProxyUuid() {
        UUID premiumUuid = UUID.randomUUID();
        given(bungeeSender.isEnabled()).willReturn(true);
        ProxySessionManager.ProxyLoginRequest request =
            new ProxySessionManager.ProxyLoginRequest("bobby", premiumUuid);
        given(proxySessionManager.getLoginRequest("Bobby")).willReturn(request);

        assertTrue(service.tryAutoRegister(player));

        assertEquals(premiumUuid, captureSavedAuth().getPremiumUuid());
    }

    @Test
    void shouldNotRegisterUnverifiedPlayer() {
        given(premiumLoginVerifier.getVerifiedUuid("Bobby")).willReturn(null);

        assertFalse(service.tryAutoRegister(player));

        verifyNoRegistration();
    }

    @Test
    void shouldNotTouchExistingAccount() {
        given(player.getUniqueId()).willReturn(UUID.randomUUID());
        given(dataSource.isAuthAvailable("bobby")).willReturn(true);

        assertFalse(service.tryAutoRegister(player));

        verifyNoRegistration();
        verify(dataSource, never()).updatePremiumUuid(any(PlayerAuth.class));
    }

    @Test
    void shouldNotRegisterWhenSettingIsDisabled() {
        given(player.getUniqueId()).willReturn(UUID.randomUUID());
        given(settings.getProperty(PremiumSettings.AUTO_REGISTER)).willReturn(false);

        assertFalse(service.tryAutoRegister(player));

        verifyNoRegistration();
    }

    @Test
    void shouldHandleDuplicateSaveWithoutException() {
        given(player.getUniqueId()).willReturn(UUID.randomUUID());
        given(dataSource.saveAuth(any(PlayerAuth.class)))
            .willThrow(new IllegalStateException("duplicate key value violates unique constraint"));

        assertDoesNotThrow(() -> service.tryAutoRegister(player));

        verify(dataSource, never()).updatePremiumUuid(any(PlayerAuth.class));
        verify(asynchronousLogin, never()).forceLogin(player);
        verify(syncProcessManager, never()).processSyncPasswordRegister(player);
    }

    @Test
    void shouldRecheckAccountAvailabilityInsideSaveTask() {
        given(player.getUniqueId()).willReturn(UUID.randomUUID());
        given(dataSource.isAuthAvailable("bobby")).willReturn(false, true);

        assertDoesNotThrow(() -> service.tryAutoRegister(player));

        verify(dataSource, times(2)).isAuthAvailable("bobby");
        verify(dataSource, never()).saveAuth(any(PlayerAuth.class));
    }

    private PlayerAuth captureSavedAuth() {
        ArgumentCaptor<PlayerAuth> authCaptor = ArgumentCaptor.forClass(PlayerAuth.class);
        verify(dataSource).saveAuth(authCaptor.capture());
        return authCaptor.getValue();
    }

    private void verifyNoRegistration() {
        verify(dataSource, never()).saveAuth(any(PlayerAuth.class));
        verify(bukkitService, never()).runTaskOptionallyAsync(any(Runnable.class));
    }

    private static UUID offlineUuid() {
        return UUID.nameUUIDFromBytes("Bobby".getBytes(StandardCharsets.UTF_8));
    }
}
