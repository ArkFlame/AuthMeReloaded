package fr.xephi.authme.platform;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.suggestion.Suggestion;
import fr.xephi.authme.command.CommandArgumentDescription;
import fr.xephi.authme.command.CommandDescription;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockMakers;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collection;
import java.util.List;

import static fr.xephi.authme.platform.BrigadierTestSupport.command;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;
import static org.mockito.Mockito.mockStatic;

/** Tab completion for force-login targets must offer every online player, authenticated or not. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.WARN)
public class PaperBrigadierSuggestionTest {

    private PaperBrigadierCommandRegistrar registrar;
    private CommandDispatcher<CommandSourceStack> dispatcher;

    @Mock(mockMaker = MockMakers.INLINE)
    private CommandSourceStack sourceStack;
    @Mock(mockMaker = MockMakers.INLINE)
    private CommandSender sender;
    @Mock(mockMaker = MockMakers.INLINE)
    private Server server;

    @BeforeEach
    public void setUp() {
        registrar = new PaperBrigadierCommandRegistrar((commandSender, parts) -> true);
        dispatcher = new CommandDispatcher<>();
        lenient().when(sourceStack.getSender()).thenReturn(sender);
        lenient().when(sender.getServer()).thenReturn(server);

        CommandDescription forcelogin = command(List.of("forcelogin", "login"), "Enforce login player",
            List.of(new CommandArgumentDescription("player", "Online player name", true)), List.of());
        CommandDescription authme = command(List.of("authme"), "AuthMe root", List.of(), List.of(forcelogin));
        CommandDescription login = command(List.of("login", "l", "log"), "Login",
            List.of(new CommandArgumentDescription("password", "Password", false)), List.of());
        for (PaperBrigadierCommandRegistration registration : registrar.buildRegistrations(List.of(authme, login))) {
            dispatcher.getRoot().addChild(registration.getNode());
        }
    }

    @Test
    public void shouldSuggestAuthenticatedAndUnauthenticatedPlayersForForceLogin() {
        doReturn(List.of(player("Alice"), player("Bob"))).when(server).getOnlinePlayers();

        assertThat(suggestionsFor("authme login "), containsInAnyOrder("Alice", "Bob"));
    }

    @Test
    public void shouldFallBackToBukkitOnlinePlayersForSuggestions() {
        doReturn(null).when(server).getOnlinePlayers();
        Collection<Player> players = List.of(player("Carol"), player("Dave"));
        try (var bukkit = mockStatic(Bukkit.class, withSettings().mockMaker(MockMakers.INLINE))) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(players);

            assertThat(suggestionsFor("authme forcelogin "), containsInAnyOrder("Carol", "Dave"));
        }
    }

    @Test
    public void shouldNotOfferPlayerNamesForThePasswordArgument() {
        doReturn(List.of(player("Alice"), player("Bob"))).when(server).getOnlinePlayers();

        assertThat(suggestionsFor("login "), hasSize(0));
    }

    private List<String> suggestionsFor(String input) {
        return dispatcher.getCompletionSuggestions(dispatcher.parse(input, sourceStack)).join().getList().stream()
            .map(Suggestion::getText)
            .toList();
    }

    private static Player player(String name) {
        Player player = mock(Player.class, withSettings().mockMaker(MockMakers.INLINE));
        given(player.getName()).willReturn(name);
        return player;
    }

}
