package fr.xephi.authme.platform;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Ensures the dialog API probe degrades gracefully instead of aborting class initialization.
 */
class DialogApiAvailabilityTest {

    @Test
    void detectsPresentDialogApi() {
        assertThat(DialogApiAvailability.isPresent(
            "net.md_5.bungee.api.dialog.MultiActionDialog", getClass().getClassLoader()), is(true));
        assertThat(DialogApiAvailability.isAvailable(), is(true));
    }

    @Test
    void treatsMissingClassAsUnavailable() {
        assertThat(DialogApiAvailability.isPresent(
            "does.not.exist.Dialog", getClass().getClassLoader()), is(false));
    }

    @Test
    void treatsLinkageFailureAsUnavailable() {
        ClassLoader failing = new ClassLoader() {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if ("broken.dialog.Unlinkable".equals(name)) {
                    throw new NoClassDefFoundError(name);
                }
                return super.loadClass(name, resolve);
            }
        };

        assertThat(DialogApiAvailability.isPresent("broken.dialog.Unlinkable", failing), is(false));
    }
}
