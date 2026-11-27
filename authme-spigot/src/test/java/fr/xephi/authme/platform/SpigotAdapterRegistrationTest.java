package fr.xephi.authme.platform;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;

/**
 * The single jar registers Folia, Paper, Spigot 1.21 and legacy adapters, most specific first, so that the first one
 * that can be loaded and finds its classes wins; on Java 17 servers the Java 21 ones fail to load and are skipped.
 */
class SpigotAdapterRegistrationTest {

    @Test
    void shouldRegisterMostSpecificAdaptersFirst() {
        List<String> names = new ArrayList<>();

        for (PlatformAdapter adapter : ServiceLoader.load(PlatformAdapter.class)) {
            names.add(adapter.getPlatformName());
        }

        assertThat(names, contains("folia-1.21", "paper-1.21", "spigot-1.21", "spigot-legacy"));
    }

    @Test
    void shouldAcceptSpigotApiOnClasspath() {
        assertThat(new SpigotLegacyPlatformAdapter().getCompatibilityError(), nullValue());
    }
}
