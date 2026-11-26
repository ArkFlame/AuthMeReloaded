package fr.xephi.authme.command.executable.authme;

import fr.xephi.authme.message.MessageKey;
import org.bukkit.Location;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

/**
 * Test for {@link SpawnCommand}.
 */
@ExtendWith(MockitoExtension.class)
class SpawnCommandTest extends AbstractSpawnTeleportCommandTest<SpawnCommand> {

    @InjectMocks
    private SpawnCommand command;

    @Override
    protected SpawnCommand getCommand() {
        return command;
    }

    @Override
    protected Location getSpawn() {
        return spawnLoader.getSpawn();
    }

    @Override
    protected void verifySpawnLoaded() {
        verify(spawnLoader, atLeastOnce()).getSpawn();
    }

    @Override
    protected MessageKey getNotDefinedMessage() {
        return MessageKey.SPAWN_NOT_DEFINED;
    }
}
