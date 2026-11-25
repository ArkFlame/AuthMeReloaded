package fr.xephi.authme.settings.properties;

import ch.jalu.configme.configurationdata.ConfigurationData;
import ch.jalu.configme.configurationdata.ConfigurationDataBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that the country code link shipped in the generated config points to a live page (#3111).
 */
class ProtectionSettingsLinkTest {

    private static final String DEAD_LINK = "https://dev.maxmind.com/geoip/legacy/codes/iso3166/";
    private static final String WORKING_LINK = "https://en.wikipedia.org/wiki/ISO_3166-1_alpha-2";

    @Test
    void shouldPointCountryCodeCommentToWorkingLink() {
        // given / when
        ConfigurationData configurationData =
            ConfigurationDataBuilder.createConfiguration(ProtectionSettings.class);
        String comments = String.join("\n",
            configurationData.getCommentsForSection(ProtectionSettings.COUNTRIES_WHITELIST.getPath()));

        // then
        assertTrue(comments.contains(WORKING_LINK));
        assertFalse(comments.contains(DEAD_LINK));
    }
}
