package fr.xephi.authme.util;

import fr.xephi.authme.TestHelper;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Test for {@link Utils}.
 */
class UtilsTest {

    @BeforeAll
    static void initLogger() {
        TestHelper.setupLogger();
    }

    @Test
    void shouldCompilePattern() {
        String pattern = "gr(a|e)ys?";

        Pattern result = Utils.safePatternCompile(pattern);

        assertThat(result.toString(), equalTo(pattern));
    }

    @Test
    void shouldDefaultToAllAllowedPattern() {
        String invalidPattern = "gr(a|eys?"; // missing closing ')'

        Pattern result = Utils.safePatternCompile(invalidPattern);

        assertThat(result.toString(), equalTo(".*?"));
    }

    @Test
    void shouldLogAndSendMessage() {
        Logger logger = TestHelper.setupLogger();
        Player player = mock(Player.class);
        String message = "Finished adding foo to the bar";

        Utils.logAndSendMessage(player, message);

        verify(logger).info(message);
        verify(player).sendMessage(message);
    }

    @Test
    void shouldHandleNullAsCommandSender() {
        Logger logger = TestHelper.setupLogger();
        String message = "Test test, test.";

        Utils.logAndSendMessage(null, message);

        verify(logger).info(message);
    }

    @Test
    void shouldNotSendToCommandSenderTwice() {
        Logger logger = TestHelper.setupLogger();
        CommandSender sender = mock(ConsoleCommandSender.class);
        String message = "Test test, test.";

        Utils.logAndSendMessage(sender, message);

        verify(logger).info(message);
        verifyNoInteractions(sender);
    }

    @Test
    void shouldCheckIfCollectionIsEmpty() {
        List<String> emptyList = Collections.emptyList();
        Collection<Integer> nonEmptyColl = Arrays.asList(3, 4, 5);

        assertThat(Utils.isCollectionEmpty(emptyList), equalTo(true));
        assertThat(Utils.isCollectionEmpty(nonEmptyColl), equalTo(false));
        assertThat(Utils.isCollectionEmpty(null), equalTo(true));
    }

    @Test
    void shouldLogAndSendWarning() {
        Logger logger = TestHelper.setupLogger();
        String message = "Error while performing action";
        CommandSender sender = mock(CommandSender.class);

        Utils.logAndSendWarning(sender, message);

        verify(logger).warning(message);
        verify(sender).sendMessage(ChatColor.RED + message);
    }

    @Test
    void shouldLogWarningAndNotSendToConsoleSender() {
        Logger logger = TestHelper.setupLogger();
        String message = "Error while performing action";
        CommandSender sender = mock(ConsoleCommandSender.class);

        Utils.logAndSendWarning(sender, message);

        verify(logger).warning(message);
        verifyNoInteractions(sender);
    }

    @Test
    void shouldLogWarningAndHandleNullCommandSender() {
        Logger logger = TestHelper.setupLogger();
        String message = "Error while performing action";
        CommandSender sender = null;

        Utils.logAndSendWarning(sender, message);

        verify(logger).warning(message);
    }

    @Test
    void shouldCheckIfClassIsLoaded() {
        assertThat(Utils.isClassLoaded("org.bukkit.event.player.PlayerFishEvent"), equalTo(true));
        assertThat(Utils.isClassLoaded("com.someclass.doesnot.exist"), equalTo(false));
    }

    @Test
    void shouldDetectIfEmailIsEmpty() {
        assertThat(Utils.isEmailEmpty(""), equalTo(true));
        assertThat(Utils.isEmailEmpty(null), equalTo(true));
        assertThat(Utils.isEmailEmpty("your@email.com"), equalTo(true));
        assertThat(Utils.isEmailEmpty("Your@Email.com"), equalTo(true));

        assertThat(Utils.isEmailEmpty("my@example.org"), equalTo(false));
        assertThat(Utils.isEmailEmpty("hey"), equalTo(false));
    }
}
