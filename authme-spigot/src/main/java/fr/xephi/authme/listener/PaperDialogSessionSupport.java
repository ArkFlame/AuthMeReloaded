package fr.xephi.authme.listener;

import io.papermc.paper.connection.PlayerConfigurationConnection;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;

/**
 * Small helpers shared by the pre-join dialog flows for completing response futures and closing dialogs.
 */
final class PaperDialogSessionSupport {

    private PaperDialogSessionSupport() {
    }

    /** Unblocks a configuration thread whose connection is gone. */
    static void releaseResponse(CompletableFuture<String> response) {
        completeIfPresent(response, null);
    }

    /**
     * Closes the dialog from the completion itself: a timeout completes the future off the configuration
     * thread, so waiting until {@code join()} returns would leave custom (Bedrock) UIs open.
     */
    static void closeDialogOnCompletion(CompletableFuture<String> response,
                                        PlayerConfigurationConnection connection) {
        response.whenComplete((kickMessage, throwable) -> {
            try {
                connection.getAudience().closeDialog();
            } catch (RuntimeException ignored) {
                // The connection may already be gone when the timeout fires.
            }
        });
    }

    static void completeResponse(ConcurrentMap<Long, CompletableFuture<String>> pendingResponses,
                                 long sessionId, String kickMessage) {
        completeIfPresent(pendingResponses.get(sessionId), kickMessage);
    }

    private static void completeIfPresent(CompletableFuture<String> response, String value) {
        if (response != null) {
            response.complete(value);
        }
    }
}
