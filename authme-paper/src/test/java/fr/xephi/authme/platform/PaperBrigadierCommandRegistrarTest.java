package fr.xephi.authme.platform;

import com.mojang.brigadier.CommandDispatcher;
import fr.xephi.authme.command.CommandArgumentDescription;
import fr.xephi.authme.command.CommandDescription;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static fr.xephi.authme.platform.BrigadierTestSupport.command;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.WARN)
public class PaperBrigadierCommandRegistrarTest {

    private PaperBrigadierCommandRegistrar registrar;
    private CommandDispatcher<CommandSourceStack> dispatcher;
    private AtomicReference<CommandSender> executingSender;
    private List<List<String>> executedCommands;

    @Mock
    private CommandSourceStack sourceStack;
    @Mock
    private CommandSender sender;

    @BeforeEach
    public void setUp() {
        executingSender = new AtomicReference<>();
        executedCommands = new ArrayList<>();
        registrar = new PaperBrigadierCommandRegistrar((commandSender, parts) -> {
            executingSender.set(commandSender);
            executedCommands.add(parts);
            return true;
        });
        dispatcher = new CommandDispatcher<>();
        lenient().when(sourceStack.getSender()).thenReturn(sender);

        for (PaperBrigadierCommandRegistration registration
            : registrar.buildRegistrations(createCommands())) {
            dispatcher.getRoot().addChild(registration.getNode());
        }
    }

    @Test
    public void shouldExposeBaseCommandAliases() {
        PaperBrigadierCommandRegistration registration = registrar.buildRegistrations(createCommands())
            .stream()
            .filter(command -> "login".equals(command.getNode().getLiteral()))
            .findFirst()
            .orElse(null);

        assertThat(registration, notNullValue());
        assertThat(registration.getAliases(), contains("l", "log"));
    }

    @ParameterizedTest
    @MethodSource("delegationCases")
    public void shouldDelegateParsedArgumentsToExistingHandler(String input, List<String> expected) throws Exception {
        dispatcher.execute(input, sourceStack);

        assertThat(executingSender.get(), equalTo(sender));
        assertThat(executedCommands, contains(expected));
    }

    private static Stream<Arguments> delegationCases() {
        return Stream.of(
            Arguments.of("authme", List.of("authme")),
            Arguments.of("authme typo", List.of("authme", "typo")),
            Arguments.of("login", List.of("login")),
            Arguments.of("login password extra", List.of("login", "password", "extra")),
            Arguments.of("authme register test@gmail.com myPassword",
                List.of("authme", "register", "test@gmail.com", "myPassword")));
    }

    @Test
    public void shouldAcceptAtSignInEmailAddArguments() throws Exception {
        String input = "email add test@example.org test@example.org";
        assertThat(dispatcher.parse(input, sourceStack).getExceptions().isEmpty(), equalTo(true));

        dispatcher.execute(input, sourceStack);

        assertThat(executedCommands, contains(List.of("email", "add", "test@example.org", "test@example.org")));
    }

    @Test
    public void shouldRegisterChildAliasesInBrigadierTree() {
        assertThat(dispatcher.getCompletionSuggestions(dispatcher.parse("authme ", sourceStack)).join()
            .getList().stream().map(suggestion -> suggestion.getText()).toList(), hasItem("reg"));
    }

    private static Collection<CommandDescription> createCommands() {
        CommandDescription authmeBase = command(List.of("authme"), "AuthMe root", List.of(), List.of());
        CommandDescription authmeRegister = command(List.of("register", "reg"),
            "Register player", List.of(
                new CommandArgumentDescription("player", "Player name", false),
                new CommandArgumentDescription("password", "Password", false)),
            List.of());
        lenient().when(authmeBase.getChildren()).thenReturn(List.of(authmeRegister));

        CommandDescription loginBase = command(List.of("login", "l", "log"),
            "Login", List.of(new CommandArgumentDescription("password", "Password", false)), List.of());
        CommandDescription emailBase = command(List.of("email"), "Email commands", List.of(), List.of());
        CommandDescription emailAdd = command(List.of("add", "addemail", "addmail"), "Add email",
            List.of(new CommandArgumentDescription("email", "Email", false),
                new CommandArgumentDescription("verifyEmail", "Email verification", false)), List.of());
        lenient().when(emailBase.getChildren()).thenReturn(List.of(emailAdd));

        return List.of(authmeBase, loginBase, emailBase);
    }

}
