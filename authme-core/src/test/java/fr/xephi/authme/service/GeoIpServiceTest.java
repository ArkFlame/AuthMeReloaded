package fr.xephi.authme.service;

import com.google.common.collect.ImmutableMap;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.model.CountryResponse;
import com.maxmind.geoip2.record.Continent;
import com.maxmind.geoip2.record.Country;
import fr.xephi.authme.settings.Settings;
import fr.xephi.authme.settings.properties.ProtectionSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.net.InetAddress;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Test for {@link GeoIpService}.
 */
@ExtendWith(MockitoExtension.class)
class GeoIpServiceTest {

    private GeoIpService geoIpService;

    @TempDir
    File dataFolder;

    @Mock
    private DatabaseReader lookupService;

    @Mock
    private BukkitService bukkitService;

    @Mock
    private Settings settings;

    @BeforeEach
    void initializeGeoLiteApi() {
        geoIpService = new GeoIpService(dataFolder, bukkitService, settings, lookupService);
    }

    @ParameterizedTest(name = "country lookup #{index}")
    @MethodSource("countryLookups")
    void shouldLookUpCountry(BiFunction<GeoIpService, String, String> lookup, String ipAddress,
                             String countryCode, String countryName, String expected) throws Exception {
        // given
        InetAddress ip = InetAddress.getByName(ipAddress);
        given(lookupService.country(ip)).willReturn(createCountryResponse(countryCode, countryName));
        given(settings.getProperty(ProtectionSettings.ENABLE_GEOIP)).willReturn(true);

        // when
        String result = lookup.apply(geoIpService, ip.getHostAddress());

        // then
        assertThat(result, equalTo(expected));
        verify(lookupService).country(ip);
    }

    private static Stream<Arguments> countryLookups() {
        return Stream.of(
            Arguments.of((BiFunction<GeoIpService, String, String>) GeoIpService::getCountryCode,
                "123.45.67.89", "XX", "Unknown", "XX"),
            Arguments.of((BiFunction<GeoIpService, String, String>) GeoIpService::getCountryName,
                "24.45.167.89", "EC", "Ecuador", "Ecuador"));
    }

    @ParameterizedTest(name = "localhost lookup #{index}")
    @MethodSource("localhostLookups")
    void shouldNotLookUpCountryForLocalhostIp(BiFunction<GeoIpService, String, String> lookup, String expected)
        throws Exception {
        // given / when
        String result = lookup.apply(geoIpService, "127.0.0.1");

        // then
        assertThat(result, equalTo(expected));
        verify(lookupService, never()).country(any());
    }

    private static Stream<Arguments> localhostLookups() {
        return Stream.of(
            Arguments.of((BiFunction<GeoIpService, String, String>) GeoIpService::getCountryCode, "LOCALHOST"),
            Arguments.of((BiFunction<GeoIpService, String, String>) GeoIpService::getCountryName, "LocalHost"));
    }

    @Test
    void shouldNotLookUpCountryNameIfDisabled() throws Exception {
        // given
        InetAddress ip = InetAddress.getByName("24.45.167.89");
        given(settings.getProperty(ProtectionSettings.ENABLE_GEOIP)).willReturn(false);

        // when
        String result = geoIpService.getCountryName(ip.getHostAddress());

        // then
        assertThat(result, equalTo("N/A"));
        verifyNoInteractions(lookupService);
    }

    private static CountryResponse createCountryResponse(String countryCode, String countryName) {
        List<String> locales = Collections.singletonList("en");
        Continent continent = new Continent(locales, "XX", 1L, Collections.emptyMap());

        Map<String, String> countryNames = ImmutableMap.of("en", countryName);
        Country country = new Country(locales, 100, 3L, false, countryCode, countryNames);
        return new CountryResponse(continent, country, null, country, null, null);
    }
}
