package fr.xephi.authme.task.purge;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for cross-thread visibility of the purge-in-progress guard.
 */
class PurgeServiceThreadSafetyTest {

    @Test
    void isPurgingMustBeVisibleAcrossSchedulerThreads() throws Exception {
        Field field = PurgeService.class.getDeclaredField("isPurging");
        assertTrue(Modifier.isVolatile(field.getModifiers()),
            "isPurging is set by the async purge task and read by the command thread");
    }
}
