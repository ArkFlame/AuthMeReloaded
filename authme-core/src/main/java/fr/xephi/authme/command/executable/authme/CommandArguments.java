package fr.xephi.authme.command.executable.authme;

import org.bukkit.command.CommandSender;

import java.util.List;

/**
 * Shared argument handling for AuthMe's admin commands.
 */
final class CommandArguments {

    private CommandArguments() {
    }

    /**
     * Returns the player name supplied as first argument, or the sender's own name if none was given.
     *
     * @param sender the command sender
     * @param arguments the command arguments
     * @return the name of the targeted player
     */
    static String getTargetPlayerName(CommandSender sender, List<String> arguments) {
        return arguments.isEmpty() ? sender.getName() : arguments.get(0);
    }
}
