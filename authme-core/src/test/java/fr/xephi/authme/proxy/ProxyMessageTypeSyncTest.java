package fr.xephi.authme.proxy;

import fr.xephi.authme.service.bungeecord.MessageType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The backend ({@link MessageType}) and the Bungee/Velocity bridges ({@link ProxyMessageType}) each keep their own
 * list of {@code authme:main} wire ids. A mismatch silently breaks proxy messaging at runtime, so the two lists
 * must stay identical.
 */
class ProxyMessageTypeSyncTest {

    @Test
    void shouldDeclareTheSameWireIdsOnBackendAndProxySide() {
        Set<String> backendIds = Arrays.stream(MessageType.values())
            .map(MessageType::getId)
            .collect(Collectors.toCollection(TreeSet::new));
        Set<String> proxyIds = Arrays.stream(ProxyMessageType.values())
            .map(ProxyMessageType::id)
            .collect(Collectors.toCollection(TreeSet::new));

        assertEquals(backendIds, proxyIds);
    }

    @Test
    void shouldResolveEveryBackendIdOnTheProxySide() {
        for (MessageType type : MessageType.values()) {
            assertEquals(type.name(), String.valueOf(ProxyMessageType.fromId(type.getId())));
        }
    }
}
