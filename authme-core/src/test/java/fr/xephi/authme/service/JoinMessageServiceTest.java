package fr.xephi.authme.service;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class JoinMessageServiceTest {

    @Test
    void shouldDropMessageRegardlessOfNameCasing() {
        BukkitService bukkitService = mock(BukkitService.class);
        JoinMessageService service = new JoinMessageService(bukkitService);

        service.putMessage("bobby", "Bobby joined");
        service.removeMessage("Bobby");
        service.sendMessage("bobby");

        verify(bukkitService, never()).broadcastMessage("Bobby joined");
    }

    @Test
    void shouldBroadcastStoredMessageOnce() {
        BukkitService bukkitService = mock(BukkitService.class);
        JoinMessageService service = new JoinMessageService(bukkitService);

        service.putMessage("bobby", "Bobby joined");
        service.sendMessage("Bobby");
        service.sendMessage("bobby");

        verify(bukkitService).broadcastMessage("Bobby joined");
    }
}
