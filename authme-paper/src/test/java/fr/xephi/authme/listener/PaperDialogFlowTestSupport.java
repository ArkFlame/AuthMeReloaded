package fr.xephi.authme.listener;

import com.destroystokyo.paper.profile.PlayerProfile;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import net.kyori.adventure.audience.Audience;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/** Reflection and mock helpers shared by the {@link PaperDialogFlowListener} tests. */
final class PaperDialogFlowTestSupport {

    static final long SESSION_ID = 42L;
    static final String PLAYER_NAME = "Bobby";

    private PaperDialogFlowTestSupport() {
    }

    static PlayerConfigurationConnection mockConnection(String playerName) {
        return mockConnection(playerName, null);
    }

    static PlayerConfigurationConnection mockConnection(String playerName, Audience audience) {
        PlayerProfile profile = mock(PlayerProfile.class);
        given(profile.getName()).willReturn(playerName);
        PlayerConfigurationConnection connection = mock(PlayerConfigurationConnection.class);
        given(connection.getProfile()).willReturn(profile);
        if (audience != null) {
            given(connection.getAudience()).willReturn(audience);
        }
        return connection;
    }

    static void setField(PaperDialogFlowListener listener, String fieldName, Object value)
        throws ReflectiveOperationException {
        Field field = PaperDialogFlowListener.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(listener, value);
    }

    @SuppressWarnings("unchecked")
    static ConcurrentMap<PlayerConfigurationConnection, Long> connectionSessions(
        PaperDialogFlowListener listener) throws ReflectiveOperationException {
        Field field = PaperDialogFlowListener.class.getDeclaredField("connectionSessions");
        field.setAccessible(true);
        return (ConcurrentMap<PlayerConfigurationConnection, Long>) field.get(listener);
    }

    @SuppressWarnings("unchecked")
    static ConcurrentMap<Long, CompletableFuture<String>> pendingResponses(
        PaperDialogFlowListener listener, String responseField) throws ReflectiveOperationException {
        Field field = PaperDialogFlowListener.class.getDeclaredField(responseField);
        field.setAccessible(true);
        return (ConcurrentMap<Long, CompletableFuture<String>>) field.get(listener);
    }

    static void invokeBlockingMethod(PaperDialogFlowListener listener, PlayerConfigurationConnection connection,
                                     String methodName, Class<?>... parameterTypes) throws ReflectiveOperationException {
        Method method = PaperDialogFlowListener.class.getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        if (parameterTypes.length == 3) {
            method.invoke(listener, connection, SESSION_ID, PLAYER_NAME);
        } else {
            method.invoke(listener, connection, SESSION_ID, PLAYER_NAME, null);
        }
    }
}
