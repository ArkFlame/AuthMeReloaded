package fr.xephi.authme.command;

import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.message.Messages;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Test for {@link PlayerCommand}.
 */
@ExtendWith(MockitoExtension.class)
class PlayerCommandTest {

    @Mock
    private Messages messages;

    @ParameterizedTest
    @MethodSource("commands")
    void shouldExecuteCommand(PlayerCommand command, CommandSender sender, List<String> arguments,
                              MessageKey key, String alternative) {
        // given
        command.messages = messages;

        // when
        command.executeCommand(sender, arguments);

        // then
        if (key == null) {
            verify(sender).sendMessage("testarg2");
        } else if (alternative == null) {
            verify(messages).send(sender, key);
        } else {
            verify(messages).send(sender, key, alternative);
        }
    }

    private static Stream<Arguments> commands() {
        return Stream.of(
            Arguments.of(new PlayerCommandImpl(), mock(CommandSender.class), Collections.emptyList(),
                MessageKey.PLAYER_COMMAND_ONLY, null),
            Arguments.of(new PlayerCommandWithAlt(), mock(CommandSender.class), Collections.emptyList(),
                MessageKey.PLAYER_COMMAND_ONLY_WITH_ALTERNATIVE, "/authme test <command>"),
            Arguments.of(new PlayerCommandImpl(), mock(Player.class), Arrays.asList("arg1", "testarg2"),
                null, null));
    }

    private static class PlayerCommandImpl extends PlayerCommand {
        @Override
        public void runCommand(Player player, List<String> arguments) {
            player.sendMessage(arguments.get(1));
        }
    }

    private static class PlayerCommandWithAlt extends PlayerCommand {
        @Override
        public void runCommand(Player player, List<String> arguments) {
            throw new IllegalStateException("Should not be called");
        }

        @Override
        public String getAlternativeCommand() {
            return "/authme test <command>";
        }
    }
}
