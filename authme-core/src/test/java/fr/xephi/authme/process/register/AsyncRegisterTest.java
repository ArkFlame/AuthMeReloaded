package fr.xephi.authme.process.register;

import ch.jalu.injector.factory.SingletonStore;
import fr.xephi.authme.TestHelper;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.data.auth.PlayerCache;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.events.AuthMeAsyncPreRegisterEvent;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.process.register.executors.ApiPasswordRegisterParams;
import fr.xephi.authme.process.register.executors.PasswordRegisterParams;
import fr.xephi.authme.process.register.executors.RegistrationExecutor;
import fr.xephi.authme.process.register.executors.RegistrationMethod;
import fr.xephi.authme.process.register.executors.TwoFactorRegisterParams;
import fr.xephi.authme.service.BukkitService;
import fr.xephi.authme.service.CommonService;
import fr.xephi.authme.settings.properties.RegistrationSettings;
import fr.xephi.authme.settings.properties.RestrictionSettings;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.function.Function;

import static fr.xephi.authme.listener.TestPlayerUtils.mockPlayerWithName;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Test for {@link AsyncRegister}.
 */
@ExtendWith(MockitoExtension.class)
class AsyncRegisterTest {

    @InjectMocks
    private AsyncRegister asyncRegister;

    @Mock
    private PlayerCache playerCache;
    @Mock
    private CommonService commonService;
    @Mock
    private BukkitService bukkitService;
    @Mock
    private DataSource dataSource;
    @Mock
    private SingletonStore<RegistrationExecutor> registrationExecutorStore;

    @Test
    void shouldDetectAlreadyLoggedInPlayer() {
        String name = "robert";
        Player player = mockPlayerWithName(name);
        given(playerCache.isAuthenticated(name)).willReturn(true);

        asyncRegister.register(RegistrationMethod.PASSWORD_REGISTRATION, PasswordRegisterParams.of(player, "abc", null));

        verify(commonService).send(player, MessageKey.ALREADY_LOGGED_IN_ERROR);
        verifyNoInteractions(dataSource, registrationExecutorStore);
    }

    @Test
    void shouldStopForDisabledRegistration() {
        String name = "albert";
        Player player = mockPlayerWithName(name);
        given(playerCache.isAuthenticated(name)).willReturn(false);
        given(commonService.getProperty(RegistrationSettings.IS_ENABLED)).willReturn(false);

        asyncRegister.register(RegistrationMethod.TWO_FACTOR_REGISTRATION, TwoFactorRegisterParams.of(player));

        verify(commonService).send(player, MessageKey.REGISTRATION_DISABLED);
        verifyNoInteractions(dataSource, registrationExecutorStore);
    }

    @Test
    void shouldStopForAlreadyRegisteredName() {
        String name = "dilbert";
        Player player = mockPlayerWithName(name);
        given(playerCache.isAuthenticated(name)).willReturn(false);
        given(commonService.getProperty(RegistrationSettings.IS_ENABLED)).willReturn(true);
        given(dataSource.isAuthAvailable(name)).willReturn(true);

        asyncRegister.register(RegistrationMethod.TWO_FACTOR_REGISTRATION, TwoFactorRegisterParams.of(player));

        verify(commonService).send(player, MessageKey.NAME_ALREADY_REGISTERED);
        verify(dataSource, only()).isAuthAvailable(name);
        verifyNoInteractions(registrationExecutorStore);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldStopForCanceledEvent() {
        String name = "edbert";
        Player player = createPlayerWithEnabledRegistration(name);
        TwoFactorRegisterParams params = TwoFactorRegisterParams.of(player);

        AuthMeAsyncPreRegisterEvent canceledEvent = new AuthMeAsyncPreRegisterEvent(player, true);
        canceledEvent.setCanRegister(false);
        given(bukkitService.createAndCallEvent(any(Function.class))).willReturn(canceledEvent);

        asyncRegister.register(RegistrationMethod.TWO_FACTOR_REGISTRATION, params);

        verify(dataSource, only()).isAuthAvailable(name);
        verifyNoInteractions(registrationExecutorStore);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldStopForFailedExecutorCheck() {
        String name = "edbert";
        Player player = createPlayerWithEnabledRegistration(name);
        given(commonService.getProperty(RestrictionSettings.MAX_REGISTRATION_PER_IP)).willReturn(0);
        RegistrationExecutor executor = mock(RegistrationExecutor.class);
        TwoFactorRegisterParams params = TwoFactorRegisterParams.of(player);
        given(executor.isRegistrationAdmitted(params)).willReturn(false);
        singletonStoreWillReturn(registrationExecutorStore, executor);

        given(bukkitService.createAndCallEvent(any(Function.class)))
            .willReturn(new AuthMeAsyncPreRegisterEvent(player, false));

        asyncRegister.register(RegistrationMethod.TWO_FACTOR_REGISTRATION, params);

        verify(dataSource, only()).isAuthAvailable(name);
        verify(executor, only()).isRegistrationAdmitted(params);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldBypassIpLimitForApiRegistration() {
        String name = "apbert";
        Player player = createPlayerWithEnabledRegistration(name);
        given(bukkitService.createAndCallEvent(any(Function.class)))
            .willReturn(new AuthMeAsyncPreRegisterEvent(player, false));
        RegistrationExecutor executor = mock(RegistrationExecutor.class);
        ApiPasswordRegisterParams params = ApiPasswordRegisterParams.of(player, "abc", false);
        given(executor.isRegistrationAdmitted(params)).willReturn(true);
        given(executor.buildPlayerAuth(params)).willReturn(PlayerAuth.builder().name(name).build());
        given(dataSource.saveAuth(any(PlayerAuth.class))).willReturn(true);
        singletonStoreWillReturn(registrationExecutorStore, executor);

        asyncRegister.register(RegistrationMethod.API_REGISTRATION, params);

        verify(commonService, never()).getProperty(RestrictionSettings.MAX_REGISTRATION_PER_IP);
        verify(executor).executePostPersistAction(params);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldRejectRegistrationWhenIpLimitIsExceeded() {
        String name = "gbert";
        Player player = createPlayerWithEnabledRegistration(name);
        TestHelper.mockIpAddressToPlayer(player, "1.2.3.4");
        given(commonService.getProperty(RestrictionSettings.MAX_REGISTRATION_PER_IP)).willReturn(1);
        given(dataSource.getAllAuthsByIp("1.2.3.4")).willReturn(Collections.singletonList("other"));
        given(bukkitService.createAndCallEvent(any(Function.class)))
            .willReturn(new AuthMeAsyncPreRegisterEvent(player, false));

        asyncRegister.register(RegistrationMethod.PASSWORD_REGISTRATION,
            PasswordRegisterParams.of(player, "abc", null));

        verify(commonService).send(player, MessageKey.MAX_REGISTER_EXCEEDED, "1", "1", "other");
        verifyNoInteractions(registrationExecutorStore);
    }

    private Player createPlayerWithEnabledRegistration(String name) {
        Player player = mockPlayerWithName(name);
        given(playerCache.isAuthenticated(name)).willReturn(false);
        given(commonService.getProperty(RegistrationSettings.IS_ENABLED)).willReturn(true);
        given(dataSource.isAuthAvailable(name)).willReturn(false);
        return player;
    }

    @SuppressWarnings("unchecked")
    private static void singletonStoreWillReturn(SingletonStore<RegistrationExecutor> store,
                                                 RegistrationExecutor mock) {
        given(store.getSingleton(any(Class.class))).willReturn(mock);
    }
}
