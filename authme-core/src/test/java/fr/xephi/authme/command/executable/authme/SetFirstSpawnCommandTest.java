package fr.xephi.authme.command.executable.authme;

import fr.xephi.authme.message.MessageKey;
import org.bukkit.Location;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

/**
 * Test for {@link SetFirstSpawnCommand}.
 */
@ExtendWith(MockitoExtension.class)
class SetFirstSpawnCommandTest extends AbstractSpawnSetterCommandTest<SetFirstSpawnCommand> {

    @InjectMocks
    private SetFirstSpawnCommand command;

    @Override
    protected SetFirstSpawnCommand getCommand() {
        return command;
    }

    @Override
    protected boolean saveSpawn(Location location) {
        return spawnLoader.setFirstSpawn(location);
    }

    @Override
    protected void verifySpawnSaved(Location location) {
        verify(spawnLoader).setFirstSpawn(location);
    }

    @Override
    protected MessageKey getSuccessMessage() {
        return MessageKey.FIRST_SPAWN_SET_SUCCESS;
    }

    @Override
    protected MessageKey getFailureMessage() {
        return MessageKey.FIRST_SPAWN_SET_FAIL;
    }
}
