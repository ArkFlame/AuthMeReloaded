package fr.xephi.authme.data.limbo;

import ch.jalu.configme.properties.Property;
import fr.xephi.authme.TestHelper;
import fr.xephi.authme.data.auth.PlayerCache;
import fr.xephi.authme.data.captcha.RegistrationCaptchaManager;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.message.Messages;
import fr.xephi.authme.service.BukkitService;
import fr.xephi.authme.service.CancellableTask;
import fr.xephi.authme.settings.Settings;
import fr.xephi.authme.settings.properties.RegistrationSettings;
import fr.xephi.authme.settings.properties.RestrictionSettings;
import fr.xephi.authme.task.MessageTask;
import fr.xephi.authme.task.TimeoutTask;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.stream.Stream;

import static fr.xephi.authme.service.BukkitService.TICKS_PER_SECOND;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Test for {@link LimboPlayerTaskManager}.
 */
@ExtendWith(MockitoExtension.class)
class LimboPlayerTaskManagerTest {

    @InjectMocks
    private LimboPlayerTaskManager limboPlayerTaskManager;

    @Mock
    private Messages messages;

    @Mock
    private Settings settings;

    @Mock
    private BukkitService bukkitService;

    @Mock
    private PlayerCache playerCache;

    @Mock
    private RegistrationCaptchaManager registrationCaptchaManager;

    @Mock
    private DataSource dataSource;

    @BeforeAll
    static void setupLogger() {
        TestHelper.setupLogger();
    }

    @Test
    void shouldRegisterMessageTask() {
        // given
        Player player = mock(Player.class);
        LimboPlayer limboPlayer = mock(LimboPlayer.class);
        MessageKey key = MessageKey.REGISTER_MESSAGE;
        given(messages.retrieveSingle(player, key)).willReturn("Please register!");
        int interval = 12;
        given(settings.getProperty(RegistrationSettings.MESSAGE_INTERVAL)).willReturn(interval);
        given(settings.getProperty(RegistrationSettings.REGISTER_MESSAGE_DELAY)).willReturn(1);
        given(bukkitService.runTaskLater(eq(player), any(Runnable.class), anyLong()))
            .willReturn(mock(CancellableTask.class));

        // when
        limboPlayerTaskManager.registerMessageTask(player, limboPlayer, LimboMessageType.REGISTER);

        // then
        verify(limboPlayer).setMessageTask(any(MessageTask.class), any(CancellableTask.class));
        verify(messages).retrieveSingle(player, key);
        verify(bukkitService).runTaskLater(eq(player), any(Runnable.class), eq(1L * TICKS_PER_SECOND));
    }

    @Test
    void shouldDelayFirstLoginMessageByConfiguredSeconds() {
        // given
        Player player = mock(Player.class);
        LimboPlayer limboPlayer = mock(LimboPlayer.class);
        given(messages.retrieveSingle(player, MessageKey.LOGIN_MESSAGE)).willReturn("Please login!");
        given(settings.getProperty(RegistrationSettings.MESSAGE_INTERVAL)).willReturn(12);
        given(settings.getProperty(RegistrationSettings.LOGIN_MESSAGE_DELAY)).willReturn(5);
        CancellableTask handle = mock(CancellableTask.class);
        given(bukkitService.runTaskTimer(eq(player), any(MessageTask.class), anyLong(), anyLong())).willReturn(handle);

        // when
        limboPlayerTaskManager.registerMessageTask(player, limboPlayer, LimboMessageType.LOG_IN);

        // then: nothing is sent before the delay (the first run is scheduled 5s out), then every interval
        verify(bukkitService).runTaskTimer(eq(player), any(MessageTask.class),
            eq(5L * TICKS_PER_SECOND), eq(12L * TICKS_PER_SECOND));
        // the handle is stored so that logging in or leaving cancels the pending message
        verify(limboPlayer).setMessageTask(any(MessageTask.class), eq(handle));
    }

    @Test
    void shouldShowLoginPromptImmediatelyByDefault() {
        // given
        Player player = mock(Player.class);
        LimboPlayer limboPlayer = mock(LimboPlayer.class);
        given(messages.retrieveSingle(player, MessageKey.LOGIN_MESSAGE)).willReturn("Please login!");
        given(settings.getProperty(RegistrationSettings.MESSAGE_INTERVAL)).willReturn(5);
        given(settings.getProperty(RegistrationSettings.LOGIN_MESSAGE_DELAY))
            .willReturn(RegistrationSettings.LOGIN_MESSAGE_DELAY.getDefaultValue());

        // when
        limboPlayerTaskManager.registerMessageTask(player, limboPlayer, LimboMessageType.LOG_IN);

        // then: fake-clock assertion - first prompt is scheduled at tick 0 (the old fixed 2s = 40 tick wait is gone)
        assertThat(RegistrationSettings.LOGIN_MESSAGE_DELAY.getDefaultValue(), equalTo(0));
        verify(bukkitService).runTaskTimer(eq(player), any(MessageTask.class),
            eq(0L), eq(5L * TICKS_PER_SECOND));
    }

    @Test
    void shouldSendLoginMessageImmediatelyForZeroOrNegativeDelay() {
        // given
        Player player = mock(Player.class);
        LimboPlayer limboPlayer = mock(LimboPlayer.class);
        given(messages.retrieveSingle(player, MessageKey.TWO_FACTOR_CODE_REQUIRED)).willReturn("Enter code");
        given(settings.getProperty(RegistrationSettings.MESSAGE_INTERVAL)).willReturn(5);
        given(settings.getProperty(RegistrationSettings.LOGIN_MESSAGE_DELAY)).willReturn(-3);

        // when
        limboPlayerTaskManager.registerMessageTask(player, limboPlayer, LimboMessageType.TOTP_CODE);

        // then
        verify(bukkitService).runTaskTimer(eq(player), any(MessageTask.class), eq(0L), eq(5L * TICKS_PER_SECOND));
    }

    @Test
    void shouldNotUseLoginDelayForRegistrationMessages() {
        // given
        Player player = mock(Player.class);
        LimboPlayer limboPlayer = mock(LimboPlayer.class);
        given(messages.retrieveSingle(player, MessageKey.REGISTER_MESSAGE)).willReturn("Please register!");
        given(settings.getProperty(RegistrationSettings.MESSAGE_INTERVAL)).willReturn(5);
        given(settings.getProperty(RegistrationSettings.REGISTER_MESSAGE_DELAY)).willReturn(1);
        given(bukkitService.runTaskLater(eq(player), any(Runnable.class), anyLong()))
            .willReturn(mock(CancellableTask.class));

        // when
        limboPlayerTaskManager.registerMessageTask(player, limboPlayer, LimboMessageType.REGISTER);

        // then
        verify(settings, never()).getProperty(RegistrationSettings.LOGIN_MESSAGE_DELAY);
    }

    @Test
    void shouldSuppressDelayedRegistrationMessageWhenPlayerWasRegistered() {
        // given
        String name = "registered_during_delay";
        Player player = mock(Player.class);
        LimboPlayer limboPlayer = mock(LimboPlayer.class);
        stubDelayedRegistrationMessage(player, limboPlayer, name, 5);
        CancellableTask lookupTask = mock(CancellableTask.class);
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return lookupTask;
        }).when(bukkitService).runTaskAsynchronously(any(Runnable.class));
        given(dataSource.isAuthAvailable(name)).willReturn(true);
        ArgumentCaptor<Runnable> delayedAction = ArgumentCaptor.forClass(Runnable.class);

        // when
        limboPlayerTaskManager.registerMessageTask(player, limboPlayer, LimboMessageType.REGISTER);
        verify(bukkitService).runTaskLater(eq(player), delayedAction.capture(), eq(1L * TICKS_PER_SECOND));
        delayedAction.getValue().run();

        // then
        verify(dataSource).isAuthAvailable(name);
        verify(player, never()).sendMessage(any(String[].class));
        verify(bukkitService, never()).runTaskTimer(eq(player), any(MessageTask.class), anyLong(), anyLong());
    }

    @Test
    void shouldSendDelayedRegistrationMessageWhenPlayerIsStillUnregistered() {
        // given
        String name = "still_unregistered";
        Player player = mock(Player.class);
        LimboPlayer limboPlayer = mock(LimboPlayer.class);
        int interval = 5;
        stubDelayedRegistrationMessage(player, limboPlayer, name, interval);
        CancellableTask lookupTask = mock(CancellableTask.class);
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return lookupTask;
        }).when(bukkitService).runTaskAsynchronously(any(Runnable.class));
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(1)).run();
            return null;
        }).when(bukkitService).scheduleSyncTaskFromOptionallyAsyncTask(eq(player), any(Runnable.class));
        given(dataSource.isAuthAvailable(name)).willReturn(false);
        given(bukkitService.runTaskTimer(eq(player), any(MessageTask.class), anyLong(), anyLong()))
            .willReturn(mock(CancellableTask.class));
        ArgumentCaptor<Runnable> delayedAction = ArgumentCaptor.forClass(Runnable.class);

        // when
        limboPlayerTaskManager.registerMessageTask(player, limboPlayer, LimboMessageType.REGISTER);
        verify(bukkitService).runTaskLater(eq(player), delayedAction.capture(), eq(1L * TICKS_PER_SECOND));
        delayedAction.getValue().run();

        // then
        verify(bukkitService).runTaskTimer(eq(player), any(MessageTask.class),
            eq((long) interval * TICKS_PER_SECOND), eq((long) interval * TICKS_PER_SECOND));
    }

    @Test
    void shouldSuppressDelayedRegistrationMessageWhenPlayerWasForceLoggedIn() {
        // given
        String name = "force_logged_in";
        Player player = mock(Player.class);
        LimboPlayer limboPlayer = mock(LimboPlayer.class);
        stubDelayedRegistrationMessage(player, limboPlayer, name, 5);
        given(playerCache.isAuthenticated(name)).willReturn(true);
        ArgumentCaptor<Runnable> delayedAction = ArgumentCaptor.forClass(Runnable.class);

        // when
        limboPlayerTaskManager.registerMessageTask(player, limboPlayer, LimboMessageType.REGISTER);
        verify(bukkitService).runTaskLater(eq(player), delayedAction.capture(), eq(1L * TICKS_PER_SECOND));
        delayedAction.getValue().run();

        // then
        verifyNoInteractions(dataSource);
        verify(player, never()).sendMessage(any(String[].class));
    }

    @Test
    void shouldNotScheduleTaskForZeroAsInterval() {
        // given
        Player player = mock(Player.class);
        LimboPlayer limboPlayer = mock(LimboPlayer.class);

        given(settings.getProperty(RegistrationSettings.MESSAGE_INTERVAL)).willReturn(0);

        // when
        limboPlayerTaskManager.registerMessageTask(player, limboPlayer, LimboMessageType.LOG_IN);

        // then
        verifyNoInteractions(limboPlayer, bukkitService);
    }

    @Test
    void shouldCancelExistingMessageTask() {
        // given
        String name = "rats";
        Player player = mock(Player.class);
        given(player.getName()).willReturn(name);
        LimboPlayer limboPlayer = new LimboPlayer(null, true, Collections.singletonList(new UserGroup("grp")), false, 0.1f, 0.0f);
        MessageTask existingMessageTask = mock(MessageTask.class);
        limboPlayer.setMessageTask(existingMessageTask);
        given(settings.getProperty(RegistrationSettings.MESSAGE_INTERVAL)).willReturn(8);
        given(settings.getProperty(RegistrationSettings.REGISTER_MESSAGE_DELAY)).willReturn(1);
        given(bukkitService.runTaskLater(eq(player), any(Runnable.class), eq(1L * TICKS_PER_SECOND)))
            .willReturn(mock(CancellableTask.class));
        given(messages.retrieveSingle(player, MessageKey.REGISTER_MESSAGE)).willReturn("Please register!");

        // when
        limboPlayerTaskManager.registerMessageTask(player, limboPlayer, LimboMessageType.REGISTER);

        // then
        assertThat(limboPlayer.getMessageTask(), not(nullValue()));
        assertThat(limboPlayer.getMessageTask(), not(sameInstance(existingMessageTask)));
        verify(registrationCaptchaManager).isCaptchaRequired(name);
        verify(messages).retrieveSingle(player, MessageKey.REGISTER_MESSAGE);
        verify(existingMessageTask).cancel();
    }

    @Test
    void shouldInitializeMessageTaskWithCaptchaMessage() {
        // given
        String name = "race";
        Player player = mock(Player.class);
        given(player.getName()).willReturn(name);
        LimboPlayer limboPlayer = new LimboPlayer(null, true, Collections.singletonList(new UserGroup("grp")), false, 0.1f, 0.0f);
        given(settings.getProperty(RegistrationSettings.MESSAGE_INTERVAL)).willReturn(12);
        given(settings.getProperty(RegistrationSettings.REGISTER_MESSAGE_DELAY)).willReturn(1);
        given(bukkitService.runTaskLater(eq(player), any(Runnable.class), eq(1L * TICKS_PER_SECOND)))
            .willReturn(mock(CancellableTask.class));
        given(registrationCaptchaManager.isCaptchaRequired(name)).willReturn(true);
        String captcha = "M032";
        given(registrationCaptchaManager.getCaptchaCodeOrGenerateNew(name)).willReturn(captcha);
        given(messages.retrieveSingle(player, MessageKey.CAPTCHA_FOR_REGISTRATION_REQUIRED, captcha)).willReturn("Need to use captcha");

        // when
        limboPlayerTaskManager.registerMessageTask(player, limboPlayer, LimboMessageType.REGISTER);

        // then
        assertThat(limboPlayer.getMessageTask(), not(nullValue()));
        verify(messages).retrieveSingle(player, MessageKey.CAPTCHA_FOR_REGISTRATION_REQUIRED, captcha);
    }

    @ParameterizedTest(name = "{0}={1} for {2}")
    @MethodSource("timeoutTaskCases")
    void shouldRegisterTimeoutTask(Property<Integer> timeoutProperty, int timeout, LimboMessageType type,
                                   long expectedTicks) {
        // given
        Player player = mock(Player.class);
        LimboPlayer limboPlayer = mock(LimboPlayer.class);
        given(settings.getProperty(timeoutProperty)).willReturn(timeout);
        CancellableTask bukkitTask = mock(CancellableTask.class);
        given(bukkitService.runTaskLater(eq(player), any(TimeoutTask.class), anyLong())).willReturn(bukkitTask);

        // when
        limboPlayerTaskManager.registerTimeoutTask(player, limboPlayer, type);

        // then
        verify(limboPlayer).setTimeoutTask(bukkitTask);
        verify(bukkitService).runTaskLater(eq(player), any(TimeoutTask.class), eq(expectedTicks));
        verify(messages).retrieveSingle(player, MessageKey.LOGIN_TIMEOUT_ERROR);
    }

    private static Stream<Arguments> timeoutTaskCases() {
        return Stream.of(
            Arguments.of(RestrictionSettings.LOGIN_TIMEOUT, 30, LimboMessageType.LOG_IN, 600L),
            Arguments.of(RestrictionSettings.REGISTER_TIMEOUT, 60, LimboMessageType.REGISTER, 1200L));
    }

    @ParameterizedTest(name = "no task for {0}=0 and {1}")
    @MethodSource("zeroTimeoutCases")
    void shouldNotRegisterTimeoutTaskForZeroTimeout(Property<Integer> timeoutProperty, LimboMessageType type) {
        // given
        Player player = mock(Player.class);
        LimboPlayer limboPlayer = mock(LimboPlayer.class);
        given(settings.getProperty(timeoutProperty)).willReturn(0);

        // when
        limboPlayerTaskManager.registerTimeoutTask(player, limboPlayer, type);

        // then
        verifyNoInteractions(limboPlayer, bukkitService);
    }

    private static Stream<Arguments> zeroTimeoutCases() {
        return Stream.of(
            Arguments.of(RestrictionSettings.LOGIN_TIMEOUT, LimboMessageType.LOG_IN),
            Arguments.of(RestrictionSettings.REGISTER_TIMEOUT, LimboMessageType.REGISTER));
    }

    @Test
    void shouldCancelExistingTimeoutTask() {
        // given
        Player player = mock(Player.class);
        LimboPlayer limboPlayer = new LimboPlayer(null, false, Collections.emptyList(), true, 0.3f, 0.1f);
        CancellableTask existingTask = mock(CancellableTask.class);
        limboPlayer.setTimeoutTask(existingTask);
        given(settings.getProperty(RestrictionSettings.LOGIN_TIMEOUT)).willReturn(18);
        CancellableTask bukkitTask = mock(CancellableTask.class);
        given(bukkitService.runTaskLater(eq(player), any(TimeoutTask.class), anyLong())).willReturn(bukkitTask);

        // when
        limboPlayerTaskManager.registerTimeoutTask(player, limboPlayer, LimboMessageType.LOG_IN);

        // then
        verify(existingTask).cancel();
        assertThat(limboPlayer.getTimeoutTask(), equalTo(bukkitTask));
        verify(bukkitService).runTaskLater(eq(player), any(TimeoutTask.class), eq(360L)); // 18 * TICKS_PER_SECOND
        verify(messages).retrieveSingle(player, MessageKey.LOGIN_TIMEOUT_ERROR);
    }

    private void stubDelayedRegistrationMessage(Player player, LimboPlayer limboPlayer, String name, int interval) {
        given(player.getName()).willReturn(name);
        given(player.isOnline()).willReturn(true);
        given(settings.getProperty(RegistrationSettings.MESSAGE_INTERVAL)).willReturn(interval);
        given(settings.getProperty(RegistrationSettings.REGISTER_MESSAGE_DELAY)).willReturn(1);
        given(messages.retrieveSingle(player, MessageKey.REGISTER_MESSAGE)).willReturn("Please register!");
        given(bukkitService.runTaskLater(eq(player), any(Runnable.class), eq(1L * TICKS_PER_SECOND)))
            .willReturn(mock(CancellableTask.class));
    }

}
