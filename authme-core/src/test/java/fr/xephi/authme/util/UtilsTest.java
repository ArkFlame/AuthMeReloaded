package fr.xephi.authme.util;

import fr.xephi.authme.TestHelper;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.logging.Logger;
import java.util.stream.Stream;

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

    // The second pattern is invalid (missing closing ')') and must default to the match-all pattern.
    @ParameterizedTest
    @CsvSource({
        "'gr(a|e)ys?', 'gr(a|e)ys?'",
        "'gr(a|eys?', '.*?'"
    })
    void shouldCompilePattern(String pattern, String expectedPattern) {
        assertThat(Utils.safePatternCompile(pattern).toString(), equalTo(expectedPattern));
    }

    @ParameterizedTest
    @MethodSource("logAndSendCases")
    void shouldLogAndSendMessage(BiConsumer<CommandSender, String> logMethod, boolean isWarning,
                                 CommandSender sender, boolean shouldReceiveMessage) {
        Logger logger = TestHelper.setupLogger();
        String message = "Finished adding foo to the bar";

        logMethod.accept(sender, message);

        if (isWarning) {
            verify(logger).warning(message);
        } else {
            verify(logger).info(message);
        }
        verifyMessageHandling(sender, isWarning ? ChatColor.RED + message : message, shouldReceiveMessage);
    }

    private static void verifyMessageHandling(CommandSender sender, String message, boolean shouldReceiveMessage) {
        if (shouldReceiveMessage) {
            verify(sender).sendMessage(message);
        } else if (sender != null) {
            verifyNoInteractions(sender);
        }
    }

    private static Stream<Arguments> logAndSendCases() {
        BiConsumer<CommandSender, String> info = Utils::logAndSendMessage;
        BiConsumer<CommandSender, String> warning = Utils::logAndSendWarning;
        return Stream.of(
            Arguments.of(info, false, mock(CommandSender.class), true),
            Arguments.of(info, false, null, false),
            Arguments.of(info, false, mock(ConsoleCommandSender.class), false),
            Arguments.of(warning, true, mock(CommandSender.class), true),
            Arguments.of(warning, true, null, false),
            Arguments.of(warning, true, mock(ConsoleCommandSender.class), false));
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
    void shouldCheckIfClassIsLoaded() {
        assertThat(Utils.isClassLoaded("org.bukkit.event.player.PlayerFishEvent"), equalTo(true));
        assertThat(Utils.isClassLoaded("com.someclass.doesnot.exist"), equalTo(false));
    }

    @Test
    void shouldDetectClassLoadedWithoutRunningStaticInitializer() {
        // The class exists but its static initializer always throws: it must not be executed.
        assertThat(Utils.isClassLoaded(FailingInitializer.class.getName()), equalTo(true));
    }

    private static final class FailingInitializer {
        static {
            if (true) {
                throw new IllegalStateException("static initializer must not be run");
            }
        }
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
