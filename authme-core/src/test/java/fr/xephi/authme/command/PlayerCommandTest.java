package fr.xephi.authme.command;

import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.message.Messages;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
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
    @MethodSource("nonPlayerCommands")
    void shouldRejectNonPlayerSender(PlayerCommand command, MessageKey key, String alternative) {
        // given
        CommandSender sender = mock(CommandSender.class);
        command.messages = messages;

        // when
        command.executeCommand(sender, Collections.emptyList());

        // then
        if (alternative == null) {
            verify(messages).send(sender, key);
        } else {
            verify(messages).send(sender, key, alternative);
        }
    }

    private static Stream<Arguments> nonPlayerCommands() {
        return Stream.of(
            Arguments.of(new PlayerCommandImpl(), MessageKey.PLAYER_COMMAND_ONLY, null),
            Arguments.of(new PlayerCommandWithAlt(), MessageKey.PLAYER_COMMAND_ONLY_WITH_ALTERNATIVE,
                "/authme test <command>"));
    }

    @Test
    void shouldCallRunCommandForPlayer() {
        // given
        Player player = mock(Player.class);
        List<String> arguments = Arrays.asList("arg1", "testarg2");
        PlayerCommandImpl command = new PlayerCommandImpl();

        // when
        command.executeCommand(player, arguments);

        // then
        verify(player).sendMessage("testarg2");
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
