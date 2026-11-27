package fr.xephi.authme.platform;

import fr.xephi.authme.command.CommandArgumentDescription;
import fr.xephi.authme.command.CommandDescription;

import java.util.List;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/** Command-description mock factory shared by the Paper Brigadier tests. */
final class BrigadierTestSupport {

    private BrigadierTestSupport() {
    }

    static CommandDescription command(List<String> labels, String description,
                                      List<CommandArgumentDescription> arguments,
                                      List<CommandDescription> children) {
        CommandDescription command = mock(CommandDescription.class);
        lenient().when(command.getLabels()).thenReturn(labels);
        lenient().when(command.getDescription()).thenReturn(description);
        lenient().when(command.getArguments()).thenReturn(arguments);
        lenient().when(command.getChildren()).thenReturn(children);
        return command;
    }
}
