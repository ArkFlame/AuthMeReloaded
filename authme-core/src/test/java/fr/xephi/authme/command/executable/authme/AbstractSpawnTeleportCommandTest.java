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
 * <p>
 * Concrete tests only supply the command under test and the spawn accessor and message it uses.
 */
@ExtendWith(MockitoExtension.class)
abstract class AbstractSpawnTeleportCommandTest<T extends SpawnTeleportCommand> {

    @Mock
    protected SpawnLoader spawnLoader;
    @Mock
    private Messages messages;
    @Mock
    private TeleportAdapter teleportAdapter;

    @Test
    void shouldTeleportToSpawn() {
        // given
        Location spawn = mock(Location.class);
        given(getSpawn()).willReturn(spawn);
        Player player = mock(Player.class);

        // when
        getCommand().executeCommand(player, Collections.emptyList());

        // then
        verifySpawnLoaded();
        verify(teleportAdapter).teleportPlayer(player, spawn);
    }

    @Test
    void shouldReportMissingSpawn() {
        // given
        given(getSpawn()).willReturn(null);
        Player player = mock(Player.class);

        // when
        getCommand().executeCommand(player, Collections.emptyList());

        // then
        verify(messages).send(player, getNotDefinedMessage());
        verifyNoInteractions(teleportAdapter);
    }

    /** @return the command under test */
    protected abstract T getCommand();

    /** Reads the spawn location through the tested command's spawn loader method. */
    protected abstract Location getSpawn();

    /** Verifies that the tested command read the spawn location. */
    protected abstract void verifySpawnLoaded();

    /** @return the message expected when no spawn is configured */
    protected abstract MessageKey getNotDefinedMessage();
}
