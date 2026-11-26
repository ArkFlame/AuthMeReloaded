package fr.xephi.authme.command.executable.authme;

import fr.xephi.authme.message.MessageKey;
import org.bukkit.Location;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

/**
 * Test for {@link FirstSpawnCommand}.
 */
@ExtendWith(MockitoExtension.class)
class FirstSpawnCommandTest extends AbstractSpawnTeleportCommandTest<FirstSpawnCommand> {

    @InjectMocks
    private FirstSpawnCommand command;

    @Override
    protected FirstSpawnCommand getCommand() {
        return command;
    }

    @Override
    protected Location getSpawn() {
        return spawnLoader.getFirstSpawn();
    }

    @Override
    protected void verifySpawnLoaded() {
        verify(spawnLoader, atLeastOnce()).getFirstSpawn();
    }

    @Override
    protected MessageKey getNotDefinedMessage() {
        return MessageKey.FIRST_SPAWN_NOT_DEFINED;
    }
}
