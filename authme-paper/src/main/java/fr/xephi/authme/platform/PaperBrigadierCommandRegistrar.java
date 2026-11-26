package fr.xephi.authme.platform;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.CommandNode;
import fr.xephi.authme.AuthMe;
import fr.xephi.authme.command.CommandArgumentDescription;
import fr.xephi.authme.command.CommandDescription;
import fr.xephi.authme.command.CommandUtils;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.CustomArgumentType;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

/**
 * Registers AuthMe commands in the Paper-derived Brigadier tree while delegating
 * all execution to the existing command handler.
 */
final class PaperBrigadierCommandRegistrar {

    private static final int COMMAND_SUCCESS = 1;
    private static final String FALLBACK_ARGUMENT_NAME = "unparsed";
    private static final String EXTRA_ARGUMENTS_NAME = "extraArgs";

    private final BiFunction<CommandSender, List<String>, Boolean> commandExecutor;

    PaperBrigadierCommandRegistrar(BiFunction<CommandSender, List<String>, Boolean> commandExecutor) {
        this.commandExecutor = commandExecutor;
    }

    void registerCommands(AuthMe plugin, Collection<CommandDescription> commands) {
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands registrar = event.registrar();
            for (PaperBrigadierCommandRegistration command : buildRegistrations(commands)) {
                registrar.register(command.getNode(), command.getDescription(), command.getAliases());
            }
        });
    }

    List<PaperBrigadierCommandRegistration> buildRegistrations(Collection<CommandDescription> commands) {
        List<PaperBrigadierCommandRegistration> registrations = new ArrayList<>(commands.size());
        for (CommandDescription command : commands) {
            List<String> labels = command.getLabels();
            registrations.add(new PaperBrigadierCommandRegistration(
                buildLiteralNode(labels.get(0), command).build(),
                command.getDescription(),
                labels.subList(1, labels.size())));
        }
        return registrations;
    }

    private LiteralArgumentBuilder<CommandSourceStack> buildLiteralNode(String label, CommandDescription command) {
        LiteralArgumentBuilder<CommandSourceStack> builder = LiteralArgumentBuilder.<CommandSourceStack>literal(label)
            .executes(this::executeInput);

        List<CommandDescription> children = command.getChildren();
        if (!children.isEmpty()) {
            for (CommandDescription child : children) {
                for (String childLabel : child.getLabels()) {
                    builder.then(buildLiteralNode(childLabel, child));
                }
            }
            builder.then(createArgument(FALLBACK_ARGUMENT_NAME, StringArgumentType.greedyString()));
            return builder;
        }

        List<CommandArgumentDescription> arguments = command.getArguments();
        if (isEmailAddCommand(command)) {
            builder.then(createArgument(FALLBACK_ARGUMENT_NAME, StringArgumentType.greedyString()));
        } else if (arguments.isEmpty()) {
            builder.then(createArgument(EXTRA_ARGUMENTS_NAME, StringArgumentType.greedyString()));
        } else {
            addArgumentChain(builder, arguments, isForceLoginTarget(command));
        }
        return builder;
    }

    private static boolean isEmailAddCommand(CommandDescription command) {
        List<String> labels = command.getLabels();
        List<CommandArgumentDescription> arguments = command.getArguments();
        return labels.contains("add") && arguments.size() == 2
            && "email".equals(arguments.get(0).getName())
            && "verifyEmail".equals(arguments.get(1).getName());
    }

    private static boolean isForceLoginTarget(CommandDescription command) {
        return command.getLabels().stream().anyMatch(label -> "login".equals(label) || "forcelogin".equals(label))
            && command.getArguments().stream().anyMatch(argument -> "player".equals(argument.getName()));
    }

    private void addArgumentChain(ArgumentBuilder<CommandSourceStack, ?> parent,
                                  List<CommandArgumentDescription> arguments, boolean suggestOnlinePlayers) {
        // Build from innermost argument outward: then(ArgumentBuilder) calls build() immediately
        // in standard Brigadier, so intermediate nodes must have their children set before building.
        int lastIndex = arguments.size() - 1;
        CommandNode<CommandSourceStack> innerChain = null;
        for (int index = lastIndex; index >= 0; --index) {
            CommandArgumentDescription argument = arguments.get(index);
            RequiredArgumentBuilder<CommandSourceStack, String> argBuilder = createArgument(argument.getName(),
                index == lastIndex ? StringArgumentType.greedyString() : AnyWordArgumentType.INSTANCE);
            if (index == lastIndex && suggestOnlinePlayers) {
                argBuilder.suggests(PaperBrigadierCommandRegistrar::suggestOnlinePlayerNames);
            }
            if (innerChain != null) {
                argBuilder.then(innerChain);
            }
            innerChain = argBuilder.build();
        }
        parent.then(innerChain);
    }

    private static CompletableFuture<Suggestions> suggestOnlinePlayerNames(CommandContext<CommandSourceStack> context,
                                                                           SuggestionsBuilder builder) {
        CommandSender sender = context.getSource().getSender();
        Server server = sender == null ? null : sender.getServer();
        Collection<? extends Player> players = server == null ? null : server.getOnlinePlayers();
        if (players == null) {
            try {
                players = Bukkit.getOnlinePlayers();
            } catch (RuntimeException e) {
                players = List.of();
            }
        }
        for (Player player : players) {
            builder.suggest(player.getName());
        }
        return builder.buildFuture();
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> createArgument(String name, ArgumentType<String> type) {
        return RequiredArgumentBuilder.<CommandSourceStack, String>argument(name, type).executes(this::executeInput);
    }

    /** Reads any non-whitespace characters; wrapped in {@link CustomArgumentType} so Paper's bridge accepts it. */
    private static final class AnyWordArgumentType implements CustomArgumentType<String, String> {
        static final AnyWordArgumentType INSTANCE = new AnyWordArgumentType();

        @Override
        public String parse(StringReader reader) throws CommandSyntaxException {
            int start = reader.getCursor();
            while (reader.canRead() && reader.peek() != ' ') {
                reader.skip();
            }
            return reader.getString().substring(start, reader.getCursor());
        }

        @Override
        public ArgumentType<String> getNativeType() {
            return StringArgumentType.word();
        }
    }

    private int executeInput(CommandContext<CommandSourceStack> context) {
        commandExecutor.apply(context.getSource().getSender(), CommandUtils.splitInput(context.getInput()));
        return COMMAND_SUCCESS;
    }
}
