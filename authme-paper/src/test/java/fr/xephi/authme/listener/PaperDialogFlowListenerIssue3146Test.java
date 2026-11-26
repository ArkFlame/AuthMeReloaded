package fr.xephi.authme.listener;

import fr.xephi.authme.data.ProxySessionManager;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.message.Messages;
import fr.xephi.authme.platform.PaperDialogHelper;
import fr.xephi.authme.service.CommonService;
import fr.xephi.authme.service.DialogWindowService;
import fr.xephi.authme.service.PreJoinDialogService;
import fr.xephi.authme.settings.properties.RestrictionSettings;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.dialog.Dialog;
import net.kyori.adventure.audience.Audience;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static fr.xephi.authme.listener.PaperDialogFlowTestSupport.SESSION_ID;
import static fr.xephi.authme.listener.PaperDialogFlowTestSupport.invokeBlockingMethod;
import static fr.xephi.authme.listener.PaperDialogFlowTestSupport.mockConnection;
import static fr.xephi.authme.listener.PaperDialogFlowTestSupport.pendingResponses;
import static fr.xephi.authme.listener.PaperDialogFlowTestSupport.setField;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/** Regression tests for dialogs left open when the configuration-phase future times out. */
public class PaperDialogFlowListenerIssue3146Test {

    @Test
    public void shouldCloseLoginDialogWhenResponseTimesOut() throws Exception {
        PaperDialogFlowListener listener = new PaperDialogFlowListener();
        CommonService commonService = mock(CommonService.class);
        Messages messages = mock(Messages.class);
        PreJoinDialogService preJoinDialogService = mock(PreJoinDialogService.class);
        ProxySessionManager proxySessionManager = mock(ProxySessionManager.class);
        DialogWindowService dialogWindowService = mock(DialogWindowService.class);
        Audience audience = mock(Audience.class);
        setField(listener, "commonService", commonService);
        setField(listener, "messages", messages);
        setField(listener, "preJoinDialogService", preJoinDialogService);
        setField(listener, "proxySessionManager", proxySessionManager);
        setField(listener, "dialogWindowService", dialogWindowService);
        given(commonService.getProperty(RestrictionSettings.LOGIN_TIMEOUT)).willReturn(1);
        given(messages.retrieveSingle("Bobby", MessageKey.LOGIN_TIMEOUT_ERROR)).willReturn("Timed out!");

        PlayerConfigurationConnection connection = mockConnection("Bobby", audience);
        dialogTimesOutWhileBlockingThreadIsBusy(listener, audience, "pendingLoginResponses");
        AtomicReference<Throwable> failure = new AtomicReference<>();

        // Static mocks are thread-local, so install it on the thread that runs the blocking method.
        Thread blockingThread = new Thread(() -> {
            try (var helperStatic = mockStatic(PaperDialogHelper.class)) {
                helperStatic.when(() -> PaperDialogHelper.createPreJoinLoginDialog(any())).thenReturn(null);
                invokeBlockingMethod(listener, connection, "handleBlockingLoginDialog",
                    PlayerConfigurationConnection.class, long.class, String.class);
            } catch (Throwable e) {
                failure.set(e);
            }
        });
        blockingThread.setDaemon(true);
        blockingThread.start();
        try {
            if (!dialogShown.await(2, TimeUnit.SECONDS)) {
                throw new AssertionError("dialog was not shown", failure.get());
            }
            // The timeout completes the future on a different thread while the configuration
            // thread is still inside showDialog(); the dialog must be closed by the timeout.
            verify(audience, timeout(1500)).closeDialog();
        } finally {
            releaseShowDialog.countDown();
            blockingThread.join(3000);
        }
        assertThat(failure.get(), is((Throwable) null));
    }

    @Test
    public void shouldCloseRegisterDialogWhenResponseTimesOut() throws Exception {
        PaperDialogFlowListener listener = new PaperDialogFlowListener();
        CommonService commonService = mock(CommonService.class);
        Messages messages = mock(Messages.class);
        PreJoinDialogService preJoinDialogService = mock(PreJoinDialogService.class);
        Audience audience = mock(Audience.class);
        setField(listener, "commonService", commonService);
        setField(listener, "messages", messages);
        setField(listener, "preJoinDialogService", preJoinDialogService);
        given(commonService.getProperty(RestrictionSettings.REGISTER_TIMEOUT)).willReturn(1);
        given(messages.retrieveSingle("Bobby", MessageKey.LOGIN_TIMEOUT_ERROR)).willReturn("Timed out!");

        PlayerConfigurationConnection connection = mockConnection("Bobby", audience);
        dialogTimesOutWhileBlockingThreadIsBusy(listener, audience, "pendingRegisterResponses");
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Thread blockingThread = new Thread(() -> {
            try {
                invokeBlockingMethod(listener, connection, "handleBlockingRegisterDialog",
                    PlayerConfigurationConnection.class, long.class, String.class, Dialog.class);
            } catch (Throwable e) {
                failure.set(e);
            }
        });
        blockingThread.setDaemon(true);
        blockingThread.start();
        try {
            assertThat("dialog should have been shown", dialogShown.await(2, TimeUnit.SECONDS), is(true));
            verify(audience, timeout(1500)).closeDialog();
        } finally {
            releaseShowDialog.countDown();
            blockingThread.join(3000);
        }
        assertThat(failure.get(), is((Throwable) null));
    }

    private CountDownLatch dialogShown;
    private CountDownLatch releaseShowDialog;

    private void dialogTimesOutWhileBlockingThreadIsBusy(PaperDialogFlowListener listener, Audience audience,
                                                         String responseField) throws Exception {
        dialogShown = new CountDownLatch(1);
        releaseShowDialog = new CountDownLatch(1);
        willAnswer(invocation -> {
            CompletableFuture<String> future = pendingResponses(listener, responseField).get(SESSION_ID);
            future.completeOnTimeout("Timed out!", 50, TimeUnit.MILLISECONDS);
            dialogShown.countDown();
            releaseShowDialog.await(3, TimeUnit.SECONDS);
            return null;
        }).given(audience).showDialog(any());
    }

}
