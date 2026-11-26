package fr.xephi.authme.command.executable.authme;

import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.message.Messages;
import fr.xephi.authme.settings.SpawnLoader;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Shared tests for commands that save the player's current location as a spawn.
 * <p>
 * Concrete tests only supply the command under test, the {@link SpawnLoader} method it uses and the
 * messages it is expected to send.
 */
@ExtendWith(MockitoExtension.class)
abstract class AbstractSpawnSetterCommandTest<T extends SpawnSetterCommand> {

    @Mock
    protected SpawnLoader spawnLoader;
    @Mock
    private Messages messages;

    private Player player;
    private Location location;

    @BeforeEach
    void setUpPlayerLocation() {
        player = mock(Player.class);
        location = mock(Location.class);
        given(player.getLocation()).willReturn(location);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldSaveSpawnAndReportOutcome(boolean saved) {
        // given
        given(saveSpawn(location)).willReturn(saved);

        // when
        getCommand().executeCommand(player, Collections.emptyList());

        // then
        verifySpawnSaved(location);
        verify(messages).send(player, saved ? getSuccessMessage() : getFailureMessage());
    }

    /** @return the command under test */
    protected abstract T getCommand();

    /** Saves the given location through the tested command's spawn loader method. */
    protected abstract boolean saveSpawn(Location location);

    /** Verifies that the tested command saved the given location. */
    protected abstract void verifySpawnSaved(Location location);

    /** @return the message expected when saving succeeded */
    protected abstract MessageKey getSuccessMessage();

    /** @return the message expected when saving failed */
    protected abstract MessageKey getFailureMessage();
}
