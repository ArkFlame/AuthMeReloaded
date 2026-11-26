package fr.xephi.authme.command.executable.authme;

import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.message.Messages;
import fr.xephi.authme.platform.TeleportAdapter;
import fr.xephi.authme.settings.SpawnLoader;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Shared tests for commands that teleport the player to a configured spawn.
 * Concrete tests supply the command, the spawn accessor and the message.
 */
@ExtendWith(MockitoExtension.class)
abstract class AbstractSpawnTeleportCommandTest<T extends SpawnTeleportCommand> {

    @Mock
    protected SpawnLoader spawnLoader;
    @Mock
    private Messages messages;
    @Mock
    private TeleportAdapter teleportAdapter;
    @Mock
    private Player player;

    @Test
    void shouldTeleportToSpawn() {
        Location spawn = mock(Location.class);
        given(getSpawn()).willReturn(spawn);

        getCommand().executeCommand(player, Collections.emptyList());

        verifySpawnLoaded();
        verify(teleportAdapter).teleportPlayer(player, spawn);
    }

    @Test
    void shouldReportMissingSpawn() {
        given(getSpawn()).willReturn(null);

        getCommand().executeCommand(player, Collections.emptyList());

        verify(messages).send(player, getNotDefinedMessage());
        verifyNoInteractions(teleportAdapter);
    }

    protected abstract T getCommand();

    protected abstract Location getSpawn();

    protected abstract void verifySpawnLoaded();

    protected abstract MessageKey getNotDefinedMessage();
}
