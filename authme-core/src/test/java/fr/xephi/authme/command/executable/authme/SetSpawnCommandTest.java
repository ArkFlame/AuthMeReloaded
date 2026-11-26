package fr.xephi.authme.command.executable.authme;

import fr.xephi.authme.message.MessageKey;
import org.bukkit.Location;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

/**
 * Test for {@link SetSpawnCommand}.
 */
@ExtendWith(MockitoExtension.class)
class SetSpawnCommandTest extends AbstractSpawnSetterCommandTest<SetSpawnCommand> {

    @InjectMocks
    private SetSpawnCommand command;

    @Override
    protected SetSpawnCommand getCommand() {
        return command;
    }

    @Override
    protected boolean saveSpawn(Location location) {
        return spawnLoader.setSpawn(location);
    }

    @Override
    protected void verifySpawnSaved(Location location) {
        verify(spawnLoader).setSpawn(location);
    }

    @Override
    protected MessageKey getSuccessMessage() {
        return MessageKey.SPAWN_SET_SUCCESS;
    }

    @Override
    protected MessageKey getFailureMessage() {
        return MessageKey.SPAWN_SET_FAIL;
    }
}
