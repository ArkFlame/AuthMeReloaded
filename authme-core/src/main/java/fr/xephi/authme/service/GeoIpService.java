package fr.xephi.authme.service;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.maxmind.db.CHMCache;
import com.maxmind.db.Reader.FileMode;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.AbstractCountryResponse;
import com.maxmind.geoip2.record.Country;
import fr.xephi.authme.ConsoleLogger;
import fr.xephi.authme.initialization.DataFolder;
import fr.xephi.authme.output.ConsoleLoggerFactory;
import fr.xephi.authme.settings.Settings;
import fr.xephi.authme.settings.properties.ProtectionSettings;
import fr.xephi.authme.util.FileUtils;
import fr.xephi.authme.util.InternetProtocolUtils;

import javax.inject.Inject;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

public class GeoIpService {

    private static final String LICENSE =
            "[LICENSE] This product includes GeoLite2 data created by MaxMind, available at https://www.maxmind.com";

    private static final String DATABASE_NAME = "GeoLite2-Country";
    private static final String DATABASE_FILE = DATABASE_NAME + ".mmdb";
    private static final String DATABASE_TMP_FILE = DATABASE_FILE + ".tmp";

    private static final String ARCHIVE_FILE = DATABASE_FILE + ".gz";

    private static final String ARCHIVE_URL =
        "https://updates.maxmind.com/geoip/databases/" + DATABASE_NAME + "/update";

    private static final int UPDATE_INTERVAL_DAYS = 30;

    // Bound the MaxMind download so a stalled connection cannot wedge the async update task
    private static final int CONNECTION_TIMEOUT_MS = 5000;

    // MD5 is 128 bits, i.e. 32 hexadecimal characters
    private static final Pattern CHECKSUM_PATTERN = Pattern.compile("[0-9a-fA-F]{32}");

    // Upper bound for the addresses remembered as having no known country
    private static final int MAX_NEGATIVE_CACHE_SIZE = 256;

    private final ConsoleLogger logger = ConsoleLoggerFactory.get(GeoIpService.class);
    private final Path dataFile;
    private final BukkitService bukkitService;
    private final Settings settings;

    // Addresses without a known country in the current database, so repeated lookups do not redo
    // the same work. Cleared whenever a new database reader is installed, keeping results identical.
    private final Cache<InetAddress, Boolean> unknownCountries = CacheBuilder.newBuilder()
        .maximumSize(MAX_NEGATIVE_CACHE_SIZE)
        .build();

    private volatile DatabaseReader databaseReader;
    private volatile boolean downloading;

    @Inject
    public GeoIpService(@DataFolder File dataFolder, BukkitService bukkitService, Settings settings) {
        this(dataFolder, bukkitService, settings, null);

        // Fires download of recent data or the initialization of the look-up service
        isDataAvailable();
    }

    @VisibleForTesting
    GeoIpService(@DataFolder File dataFolder, BukkitService bukkitService, Settings settings, DatabaseReader reader) {
        this.bukkitService = bukkitService;
        this.settings = settings;
        this.dataFile = dataFolder.toPath().resolve(DATABASE_FILE);
        this.databaseReader = reader;
    }

    /**
     * Download (if absent or old) the GeoIpLite data file and then try to load it.
     *
     * @return True if the data is available, false otherwise.
     */
    private synchronized boolean isDataAvailable() {

        // If this feature is disabled or an update download is in progress, stop
        if (!settings.getProperty(ProtectionSettings.ENABLE_GEOIP) || downloading) {
            return false;
        }

        if (databaseReader != null) {
            // everything is initialized
            return true;
        }

        if (Files.exists(dataFile)) {
            try {
                FileTime lastModifiedTime = Files.getLastModifiedTime(dataFile);
                if (Duration.between(lastModifiedTime.toInstant(), Instant.now()).toDays() <= UPDATE_INTERVAL_DAYS) {
                    startReading();
                    // don't fire the update task - we are up-to-date
                    return true;
                }
                logger.debug("GEO IP database is older than " + UPDATE_INTERVAL_DAYS + " Days");
            } catch (IOException ioEx) {
                logger.logException("Failed to load GeoLiteAPI database", ioEx);
                return false;
            }
        }

        //set the downloading flag in order to fix race conditions outside
        downloading = true;

        // File is outdated or doesn't exist - let's try to download the data file!
        // use bukkit's cached threads
        bukkitService.runTaskAsynchronously(this::updateDatabase);
        return false;
    }

    /**
     * Tries to update the database by downloading a new version from the website.
     */
    private void updateDatabase() {
        logger.info("Downloading GEO IP database, because the old database is older than "
                + UPDATE_INTERVAL_DAYS + " days or doesn't exist");

        Path downloadFile = null;
        Path tempFile = null;
        try {
            // download database to temporarily location
            downloadFile = Files.createTempFile(ARCHIVE_FILE, null);
            tempFile = Files.createTempFile(DATABASE_TMP_FILE, null);
            Instant lastModified = Files.exists(dataFile)
                ? Files.getLastModifiedTime(dataFile).toInstant()
                : null;
            String expectedChecksum = downloadDatabaseArchive(lastModified, downloadFile);
            if (expectedChecksum == null) {
                logger.info("There is no newer GEO IP database uploaded to MaxMind. Using the old one for now.");
                startReading();
                return;
            }

            // tar extract database and copy to target destination
            extractDatabase(downloadFile, tempFile);

            // MD5 checksum verification
            //noinspection deprecation
            verifyChecksum(Hashing.md5(), tempFile, expectedChecksum);

            Files.copy(tempFile, dataFile, StandardCopyOption.REPLACE_EXISTING);

            //only set this value to false on success otherwise errors could lead to endless download triggers
            logger.info("Successfully downloaded new GEO IP database to " + dataFile);
            startReading();
        } catch (IOException ioEx) {
            logger.logException("Could not download GeoLiteAPI database", ioEx);
        } finally {
            // clean up
            deleteIfPresent(downloadFile);
            deleteIfPresent(tempFile);
        }
    }

    private static void deleteIfPresent(Path file) {
        if (file != null) {
            FileUtils.delete(file.toFile());
        }
    }

    private void startReading() throws IOException {
        databaseReader = new DatabaseReader.Builder(dataFile.toFile())
            .withCache(new CHMCache())
            .fileMode(FileMode.MEMORY)
            .build();
        // The data changed: previously unknown addresses may be resolvable now
        unknownCountries.invalidateAll();
        logger.info(LICENSE);

        // clear downloading flag, because we now have working reader instance
        downloading = false;
    }

    /**
     * Downloads the archive to the destination file if it's newer than the local version.
     *
     * @param lastModified modification timestamp of the already present file
     * @param destination save file
     * @return null if no updates were found, the MD5 hash of the downloaded archive if successful
     * @throws IOException if failed during downloading and writing to destination file
     */
    private String downloadDatabaseArchive(Instant lastModified, Path destination) throws IOException {
        HttpURLConnection connection = openConnection(lastModified);
        if (connection == null) {
            return null;
        }

        try {
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
                //we already have the newest version
                return null;
            }
            if (responseCode != HttpURLConnection.HTTP_OK) {
                // Anything else (e.g. bad credentials, rate limit or server error) is not a database archive
                throw new IOException("Failed to download GEO IP database: MaxMind responded with HTTP " + responseCode);
            }

            String hash = connection.getHeaderField("X-Database-MD5");
            Instant modifiedDate = parseModifiedDate(connection.getHeaderField("Last-Modified"));
            Files.copy(connection.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
            Files.setLastModifiedTime(destination, FileTime.from(modifiedDate));
            return hash;
        } finally {
            // Files.copy does not close the source stream, so release the socket on every path
            connection.disconnect();
        }
    }

    /**
     * Parses the {@code Last-Modified} header, turning missing or malformed values into a checked
     * failure so the async update task logs it instead of aborting with an unchecked exception.
     */
    private static Instant parseModifiedDate(String rawModifiedDate) throws IOException {
        if (rawModifiedDate == null) {
            throw new IOException("Missing Last-Modified header in MaxMind response");
        }
        try {
            return Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(rawModifiedDate));
        } catch (DateTimeException e) {
            throw new IOException("Invalid Last-Modified header in MaxMind response: " + rawModifiedDate, e);
        }
    }

    /**
     * Opens the download connection with authentication and, when known, the last modification date.
     */
    private HttpURLConnection openConnection(Instant lastModified) throws IOException {
        String clientId = settings.getProperty(ProtectionSettings.MAXMIND_API_CLIENT_ID);
        String licenseKey = settings.getProperty(ProtectionSettings.MAXMIND_API_LICENSE_KEY);
        if (clientId.isEmpty() || licenseKey.isEmpty()) {
            logger.warning("No MaxMind credentials found in the configuration file!"
                + " GeoIp protections will be disabled.");
            return null;
        }

        HttpURLConnection connection = (HttpURLConnection) new URL(ARCHIVE_URL).openConnection();
        configureConnection(connection);
        String basicAuth = "Basic " + new String(Base64.getEncoder().encode((clientId + ":" + licenseKey).getBytes()));
        connection.setRequestProperty("Authorization", basicAuth);

        if (lastModified != null) {
            // Only download if we actually need a newer version - this field is specified in GMT zone
            ZonedDateTime zonedTime = lastModified.atZone(ZoneId.of("GMT"));
            connection.addRequestProperty("If-Modified-Since", DateTimeFormatter.RFC_1123_DATE_TIME.format(zonedTime));
        }
        return connection;
    }

    /**
     * Applies bounded connect and read timeouts before any network I/O is performed.
     */
    private static void configureConnection(HttpURLConnection connection) {
        connection.setConnectTimeout(CONNECTION_TIMEOUT_MS);
        connection.setReadTimeout(CONNECTION_TIMEOUT_MS);
    }

    /**
     * Verify if the expected checksum is equal to the checksum of the given file.
     *
     * @param function the checksum function like MD5, SHA256 used to generate the checksum from the file
     * @param file the file we want to calculate the checksum from
     * @param expectedChecksum the expected checksum
     * @throws IOException on I/O error reading the file or the checksum verification failed
     */
    private void verifyChecksum(HashFunction function, Path file, String expectedChecksum) throws IOException {
        if (expectedChecksum == null || !CHECKSUM_PATTERN.matcher(expectedChecksum).matches()) {
            throw new IOException("Invalid or missing MaxMind checksum header");
        }
        HashCode actualHash = function.hashBytes(Files.readAllBytes(file));
        if (!actualHash.equals(HashCode.fromString(expectedChecksum))) {
            throw new IOException("GEO IP Checksum verification failed. "
                + "Expected: " + expectedChecksum + "Actual:" + actualHash);
        }
    }

    /**
     * Extract the database from gzipped data. Existing outputFile will be replaced if it already exists.
     *
     * @param inputFile gzipped database input file
     * @param outputFile destination file for the database
     * @throws IOException on I/O error reading the archive, or writing the output
     */
    private void extractDatabase(Path inputFile, Path outputFile) throws IOException {
        // .gz -> gzipped file
        try (GZIPInputStream gzipIn = new GZIPInputStream(
                new BufferedInputStream(Files.newInputStream(inputFile)))) {

            // found the database file and copy file
            Files.copy(gzipIn, outputFile, StandardCopyOption.REPLACE_EXISTING);

            // update the last modification date to be same as in the archive
            Files.setLastModifiedTime(outputFile, Files.getLastModifiedTime(inputFile));
        }
    }

    /**
     * Get the country code of the given IP address.
     *
     * @param ip textual IP address to lookup.
     * @return two-character ISO 3166-1 alpha code for the country, "LOCALHOST" for local addresses
     *         or "--" if it cannot be fetched.
     */
    public String getCountryCode(String ip) {
        return getCountryValue(ip, Country::getIsoCode, "--", "LOCALHOST");
    }

    /**
     * Get the country name of the given IP address.
     *
     * @param ip textual IP address to lookup.
     * @return The name of the country, "LocalHost" for local addresses, or "N/A" if it cannot be fetched.
     */
    public String getCountryName(String ip) {
        return getCountryValue(ip, Country::getName, "N/A", "LocalHost");
    }

    private String getCountryValue(String ip, Function<Country, String> extractor, String fallback,
                                   String localAddressValue) {
        if (InternetProtocolUtils.isLocalAddress(ip)) {
            return localAddressValue;
        }
        if (ip == null || ip.isEmpty() || !isDataAvailable()) {
            return fallback;
        }

        try {
            InetAddress address = InetAddress.getByName(ip);
            // A miss for the current database cannot change until a new reader is installed
            if (unknownCountries.getIfPresent(address) != null) {
                return fallback;
            }
            AbstractCountryResponse response = databaseReader.country(address);
            // Reader.getCountry() can be null for unknown addresses
            Country country = response == null ? null : response.getCountry();
            if (country == null) {
                unknownCountries.put(address, Boolean.TRUE);
                return fallback;
            }
            return extractor.apply(country);
        } catch (UnknownHostException e) {
            // Ignore invalid ip addresses
            // Legacy GEO IP Database returned an unknown country object with Country-Code: '--' and Country-Name: 'N/A'
        } catch (GeoIp2Exception | IOException ioEx) {
            logger.logException("Cannot lookup country for " + ip + " at GEO IP database", ioEx);
        }

        return fallback;
    }
}
