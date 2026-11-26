package fr.xephi.authme.message;

import fr.xephi.authme.platform.BukkitCompatibilityAdapter;
import fr.xephi.authme.settings.Settings;
import fr.xephi.authme.settings.properties.PluginSettings;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * Test for {@link PlayerLocaleResolver}.
 */
class PlayerLocaleResolverTest {

    @ParameterizedTest
    @CsvSource({
        "fr_fr, fr",
        "de_de, de",
        "en_us, en",
        "en_gb, en",
        "ru_ru, ru",
        "it_it, it",
        "nl_nl, nl",
        "pl_pl, pl",
        "ko_kr, ko",
        "ja_jp, ja",
        "tr_tr, tr",
        // special cases: locale without underscore
        "fr,    fr",
        "de,    de",
        // special overrides
        "pt_br, br",
        "zh_cn, zhcn",
        "zh_tw, zhtw",
        "zh_hk, zhhk",
    })
    void shouldMapLocaleToLanguageCode(String locale, String expected) {
        assertThat(PlayerLocaleResolver.toLanguageCode(locale), equalTo(expected));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void shouldReturnNullForBlankOrNullInput(String locale) {
        assertThat(PlayerLocaleResolver.toLanguageCode(locale), nullValue());
    }

    private static Settings settingsWithPerPlayerLocale(boolean enabled) {
        Settings settings = mock(Settings.class);
        given(settings.getProperty(PluginSettings.PER_PLAYER_LOCALE)).willReturn(enabled);
        return settings;
    }

    private static BukkitCompatibilityAdapter adapterReturningLocale(Player player, String locale) {
        BukkitCompatibilityAdapter adapter = mock(BukkitCompatibilityAdapter.class);
        given(adapter.getPlayerLocale(player)).willReturn(Optional.of(locale));
        return adapter;
    }

    @ParameterizedTest
    @CsvSource({
        "fr_fr, fr, false",
        "pt_br, br, false",
        ",, true"
    })
    void shouldResolveLanguageForPlayerWithPerPlayerLocale(String locale, String expectedLanguage, boolean empty) {
        Player player = mock(Player.class);
        BukkitCompatibilityAdapter adapter = mock(BukkitCompatibilityAdapter.class);
        given(adapter.getPlayerLocale(player)).willReturn(empty ? Optional.empty() : Optional.of(locale));
        Settings settings = settingsWithPerPlayerLocale(true);
        if (empty) {
            assertThat(PlayerLocaleResolver.resolveLanguage(settings, player, adapter), nullValue());
        } else {
            assertThat(PlayerLocaleResolver.resolveLanguage(settings, player, adapter), equalTo(expectedLanguage));
        }
    }

    @ParameterizedTest
    @CsvSource({"true", "false"})
    void shouldReturnNullWhenAdapterIsNull(boolean enabled) {
        // given per-player locale enabled but no adapter capability
        Player player = mock(Player.class);
        Settings settings = settingsWithPerPlayerLocale(enabled);

        assertThat(PlayerLocaleResolver.resolveLanguage(settings, player, null), nullValue());
    }

    @ParameterizedTest
    @CsvSource({"true", "false"})
    void shouldReturnNullForPlayerWithPerPlayerLocaleDisabled(boolean enabled) {
        Player player = mock(Player.class);
        BukkitCompatibilityAdapter adapter = mock(BukkitCompatibilityAdapter.class);
        Settings settings = settingsWithPerPlayerLocale(enabled);

        assertThat(PlayerLocaleResolver.resolveLanguage(settings, player, adapter), nullValue());
    }

    @Test
    void shouldNotDependOnTheDefaultLocaleWhenNormalizingCase() {
        java.util.Locale previous = java.util.Locale.getDefault();
        java.util.Locale.setDefault(new java.util.Locale("tr", "TR"));
        try {
            assertThat(PlayerLocaleResolver.toLanguageCode("IT_IT"), equalTo("it"));
        } finally {
            java.util.Locale.setDefault(previous);
        }
    }

    @Test
    void shouldReturnNullForNonPlayerSender() {
        CommandSender sender = mock(CommandSender.class);
        BukkitCompatibilityAdapter adapter = mock(BukkitCompatibilityAdapter.class);
        Settings settings = settingsWithPerPlayerLocale(true);

        assertThat(PlayerLocaleResolver.resolveLanguage(settings, sender, adapter), nullValue());
    }

    @Test
    void shouldReturnNullForNullSettings() {
        Player player = mock(Player.class);
        BukkitCompatibilityAdapter adapter = mock(BukkitCompatibilityAdapter.class);

        assertThat(PlayerLocaleResolver.resolveLanguage(null, player, adapter), nullValue());
    }
}
