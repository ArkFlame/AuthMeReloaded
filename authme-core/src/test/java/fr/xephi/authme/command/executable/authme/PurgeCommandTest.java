package fr.xephi.authme.command.executable.authme;

import fr.xephi.authme.task.purge.PurgeService;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Calendar;
import java.util.Collections;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.hamcrest.MockitoHamcrest.argThat;

/**
 * Test for {@link PurgeCommand}.
 */
@ExtendWith(MockitoExtension.class)
class PurgeCommandTest {

    @InjectMocks
    private PurgeCommand command;

    @Mock
    private PurgeService purgeService;

    @ParameterizedTest
    @CsvSource(value = {
        "invalid, The value you've entered is invalid",
        "29,      You can only purge data older than 30 days"
    }, quoteCharacter = '"')
    void shouldRejectInvalidInterval(String interval, String expectedMessage) {
        // given
        CommandSender sender = mock(CommandSender.class);

        // when
        command.executeCommand(sender, Collections.singletonList(interval));

        // then
        verify(sender).sendMessage(argThat(containsString(expectedMessage)));
        verifyNoInteractions(purgeService);
    }

    @Test
    void shouldForwardToService() {
        // given
        String interval = "45";
        CommandSender sender = mock(CommandSender.class);

        // when
        long before = daysAgo(Integer.parseInt(interval));
        command.executeCommand(sender, Collections.singletonList(interval));
        long after = daysAgo(Integer.parseInt(interval));

        // then: the timestamp lies between the two reference points, independent of machine load
        ArgumentCaptor<Long> captor = ArgumentCaptor.forClass(Long.class);
        verify(purgeService).runPurge(eq(sender), captor.capture());
        assertThat(captor.getValue(), allOf(greaterThanOrEqualTo(before), lessThanOrEqualTo(after)));
    }

    private static long daysAgo(int days) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DATE, -days);
        return calendar.getTimeInMillis();
    }

}
