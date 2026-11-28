package fr.xephi.authme.service;

import com.google.common.collect.ImmutableMap;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
    private Settings settings;

    @BeforeEach
    void initializeGeoLiteApi() {
        geoIpService = new GeoIpService(dataFolder, null, settings, lookupService);
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
        given(settings.getProperty(ProtectionSettings.ENABLE_GEOIP)).willReturn(false);

        // when
        String result = geoIpService.getCountryName("24.45.167.89");

        // then
        assertThat(result, equalTo("N/A"));
        verifyNoInteractions(lookupService);
    }

    @Test
    void shouldCacheNegativeLookupResults() throws Exception {
        // given
        InetAddress ip = InetAddress.getByName("24.45.167.89");
        given(lookupService.country(ip)).willReturn(null);
        given(settings.getProperty(ProtectionSettings.ENABLE_GEOIP)).willReturn(true);

        // when
        String firstResult = geoIpService.getCountryName(ip.getHostAddress());
        String secondResult = geoIpService.getCountryName(ip.getHostAddress());

        // then
        assertThat(firstResult, equalTo("N/A"));
        assertThat(secondResult, equalTo("N/A"));
        verify(lookupService).country(ip);
    }

    @Test
    void shouldRejectMissingChecksumAsCheckedFailure() throws Exception {
        // given
        Path database = dataFolder.toPath().resolve("GeoLite2-Country.mmdb");
        Files.write(database, new byte[0]);
        Method verifyChecksum = GeoIpService.class.getDeclaredMethod("verifyChecksum",
            HashFunction.class, Path.class, String.class);
        verifyChecksum.setAccessible(true);

        // when
        InvocationTargetException result = assertThrows(InvocationTargetException.class,
            () -> verifyChecksum.invoke(geoIpService, Hashing.md5(), database, null));

        // then
        assertThat(result.getCause(), instanceOf(IOException.class));
        assertThat(result.getCause().getMessage(), containsString("checksum"));
    }

    @Test
    void shouldRetryFailedDownloadOnlyAfterCooldown() throws Exception {
        // given
        BukkitService bukkitService = mock(BukkitService.class);
        given(settings.getProperty(ProtectionSettings.ENABLE_GEOIP)).willReturn(true);
        GeoIpService service = new GeoIpService(dataFolder, bukkitService, settings, null);
        ArgumentCaptor<Runnable> downloadTask = ArgumentCaptor.forClass(Runnable.class);

        // when: the first lookup schedules a download, which fails (no credentials and no database file)
        assertThat(service.getCountryCode("24.45.167.89"), equalTo("--"));
        verify(bukkitService).runTaskAsynchronously(downloadTask.capture());
        downloadTask.getValue().run();

        // then: lookups within the cooldown do not schedule another download
        assertThat(service.getCountryCode("24.45.167.89"), equalTo("--"));
        verify(bukkitService, times(1)).runTaskAsynchronously(any());

        // and once the cooldown has passed, the download is attempted again instead of staying disabled
        Field retryNotBefore = GeoIpService.class.getDeclaredField("retryNotBefore");
        retryNotBefore.setAccessible(true);
        retryNotBefore.setLong(service, 0L);
        service.getCountryCode("24.45.167.89");
        verify(bukkitService, times(2)).runTaskAsynchronously(any());
    }

    @Test
    void shouldSetBoundedTimeoutsOnDownloadConnection() throws Exception {
        // given
        HttpURLConnection connection = mock(HttpURLConnection.class);
        Method configureConnection = GeoIpService.class.getDeclaredMethod("configureConnection", HttpURLConnection.class);
        configureConnection.setAccessible(true);

        // when
        configureConnection.invoke(null, connection);

        // then
        verify(connection).setConnectTimeout(5000);
        verify(connection).setReadTimeout(5000);
    }

    private static CountryResponse createCountryResponse(String countryCode, String countryName) {
        List<String> locales = Collections.singletonList("en");
        Continent continent = new Continent(locales, "XX", 1L, Collections.emptyMap());

        Country country = new Country(locales, 100, 3L, false, countryCode, ImmutableMap.of("en", countryName));
        return new CountryResponse(continent, country, null, country, null, null);
    }
}
