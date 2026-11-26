package fr.xephi.authme.platform;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;

/**
 * The single Spigot jar registers two adapters. The Java 21 adapter must come first so that it is preferred
 * where it can be loaded; on Java 17 servers loading it fails and the legacy adapter is used instead.
 */
class SpigotAdapterRegistrationTest {

    @Test
    void shouldRegisterJava21AdapterBeforeLegacyAdapter() {
        // given
        List<String> names = new ArrayList<>();

        // when
        for (PlatformAdapter adapter : ServiceLoader.load(PlatformAdapter.class)) {
            names.add(adapter.getPlatformName());
        }

        // then
        assertThat(names, contains("spigot-1.21", "spigot-legacy"));
    }

    @Test
    void shouldAcceptSpigotApiOnClasspath() {
        // given / when / then
        assertThat(new SpigotLegacyPlatformAdapter().getCompatibilityError(), nullValue());
    }
}
