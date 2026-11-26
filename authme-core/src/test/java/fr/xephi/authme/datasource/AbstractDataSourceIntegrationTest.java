package fr.xephi.authme.datasource;

import ch.jalu.configme.properties.Property;
import ch.jalu.datasourcecolumns.data.DataSourceValue;
import ch.jalu.datasourcecolumns.data.DataSourceValueImpl;
import com.google.common.collect.Lists;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.security.crypts.HashedPassword;
import fr.xephi.authme.settings.Settings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static fr.xephi.authme.AuthMeMatchers.equalToHash;
import static fr.xephi.authme.AuthMeMatchers.hasAuthBasicData;
import static fr.xephi.authme.AuthMeMatchers.hasAuthLocation;
import static fr.xephi.authme.AuthMeMatchers.hasRegistrationInfo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;

/**
 * Abstract class for data source integration tests.
 */
public abstract class AbstractDataSourceIntegrationTest {

    private DataSource getDataSource() {
        return getDataSource("salt");
    }

    protected abstract DataSource getDataSource(String saltColumn);

    /** Sets a database setting on the given mock settings instance. */
    protected static <T> void setProperty(Settings settings, Property<T> property, T value) {
        when(settings.getProperty(property)).thenReturn(value);
    }

    private static void saveAuthAndUpdateSession(DataSource dataSource, PlayerAuth auth) {
        dataSource.saveAuth(auth);
        dataSource.updateSession(auth);
    }

    @Test
    void shouldReturnIfAuthIsAvailableOrNot() {
        DataSource dataSource = getDataSource();

        boolean isBobbyAvailable = dataSource.isAuthAvailable("bobby");
        boolean isChrisAvailable = dataSource.isAuthAvailable("chris");
        boolean isUserAvailable = dataSource.isAuthAvailable("USER");

        assertThat(isBobbyAvailable, equalTo(true));
        assertThat(isChrisAvailable, equalTo(false));
        assertThat(isUserAvailable, equalTo(true));
    }

    @ParameterizedTest(name = "salt column ''{0}'', name ''{1}''")
    @MethodSource("passwordCases")
    void shouldReturnPassword(String saltColumn, String userName, HashedPassword expectedUserPassword) {
        DataSource dataSource = getDataSource(saltColumn);

        HashedPassword invalidPassword = dataSource.getPassword("doesNotExist");
        HashedPassword userPassword = dataSource.getPassword(userName);

        assertThat(invalidPassword, nullValue());
        assertThat(userPassword, equalToHash(expectedUserPassword));
    }

    private static Stream<Arguments> passwordCases() {
        String hash = "b28c32f624a4eb161d6adc9acb5bfc5b";
        return Stream.of(
            Arguments.of("salt", "User", new HashedPassword(hash, "f750ba32")),
            Arguments.of("", "user", new HashedPassword(hash)));
    }

    @Test
    void shouldGetAuth() {
        DataSource dataSource = getDataSource();

        PlayerAuth invalidAuth = dataSource.getAuth("notInDB");
        PlayerAuth bobbyAuth = dataSource.getAuth("Bobby");
        PlayerAuth userAuth = dataSource.getAuth("user");

        assertThat(invalidAuth, nullValue());

        assertThat(bobbyAuth, hasAuthBasicData("bobby", "Bobby", null, "123.45.67.89"));
        assertThat(bobbyAuth, hasAuthLocation(1.05, 2.1, 4.2, "world", -0.44f, 2.77f));
        assertThat(bobbyAuth, hasRegistrationInfo("127.0.4.22", 1436778723L));
        assertThat(bobbyAuth.getLastLogin(), equalTo(1449136800L));
        assertThat(bobbyAuth.getPassword(), equalToHash("$SHA$11aa0706173d7272$dbba966"));
        assertThat(bobbyAuth.getTotpKey(), equalTo("JBSWY3DPEHPK3PXP"));

        assertThat(userAuth, hasAuthBasicData("user", "user", "user@example.org", "34.56.78.90"));
        assertThat(userAuth, hasAuthLocation(124.1, 76.3, -127.8, "nether", 0.23f, 4.88f));
        assertThat(userAuth, hasRegistrationInfo(null, 0));
        assertThat(userAuth.getLastLogin(), equalTo(1453242857L));
        assertThat(userAuth.getPassword(), equalToHash("b28c32f624a4eb161d6adc9acb5bfc5b", "f750ba32"));
        assertThat(userAuth.getTotpKey(), nullValue());
    }

    @Test
    void shouldSaveAuthAndCountAndList() {
        DataSource dataSource = getDataSource();

        int userMailCount = dataSource.countAuthsByEmail("user@example.ORG");
        int invalidMailCount = dataSource.countAuthsByEmail("not.in.db@example.com");
        List<PlayerAuth> authList = dataSource.getAllAuths();
        boolean response = dataSource.saveAuth(
            PlayerAuth.builder().name("Test").email("user@EXAMPLE.org").build());
        int newUserCount = dataSource.countAuthsByEmail("user@Example.org");
        List<PlayerAuth> newAuthList = dataSource.getAllAuths();

        assertThat(userMailCount, equalTo(1));
        assertThat(invalidMailCount, equalTo(0));
        assertThat(authList, hasSize(2));
        assertThat(response, equalTo(true));
        assertThat(newUserCount, equalTo(2));
        assertThat(newAuthList, hasSize(3));
        assertThat(newAuthList, hasItem(hasAuthBasicData("bobby", "Bobby", null, "123.45.67.89")));
    }

    @ParameterizedTest(name = "salt column ''{0}''")
    @MethodSource("updatePasswordCases")
    void shouldUpdatePassword(String saltColumn, HashedPassword newHash, HashedPassword invalidHash,
                              String expectedHash) {
        DataSource dataSource = getDataSource(saltColumn);

        boolean response1 = dataSource.updatePassword("user", newHash);
        boolean response2 = dataSource.updatePassword("non-existent-name", invalidHash);

        assertThat(response1, equalTo(true));
        assertThat(response2, equalTo(false)); // no record modified
        assertThat(dataSource.getPassword("user"), equalToHash(expectedHash));
    }

    private static Stream<Arguments> updatePasswordCases() {
        return Stream.of(
            Arguments.of("salt", new HashedPassword("new_hash"), new HashedPassword("sd"), "new_hash"),
            Arguments.of("", new HashedPassword("new_hash", "1241"),
                new HashedPassword("asdfasdf", "a1f34ec"), "new_hash"));
    }

    @Test
    void shouldUpdatePasswordWithPlayerAuth() {
        DataSource dataSource = getDataSource("salt");
        PlayerAuth bobbyAuth = PlayerAuth.builder().name("bobby").password(new HashedPassword("tt", "cc")).build();
        PlayerAuth invalidAuth = PlayerAuth.builder().name("invalid").password(new HashedPassword("tt", "cc")).build();

        boolean response1 = dataSource.updatePassword(bobbyAuth);
        boolean response2 = dataSource.updatePassword(invalidAuth);

        assertThat(response1, equalTo(true));
        assertThat(response2, equalTo(false)); // no record modified
        assertThat(dataSource.getPassword("bobby"), equalToHash("tt", "cc"));
    }

    @Test
    void shouldRemovePlayerAuth() {
        DataSource dataSource = getDataSource();

        boolean response1 = dataSource.removeAuth("Bobby");
        boolean response2 = dataSource.removeAuth("does-not-exist");

        assertThat(response1 && response2, equalTo(true));
        assertThat(dataSource.getAuth("bobby"), nullValue());
        assertThat(dataSource.isAuthAvailable("bobby"), equalTo(false));
    }

    @Test
    void shouldUpdateSession() {
        DataSource dataSource = getDataSource();
        PlayerAuth bobby = PlayerAuth.builder()
            .name("bobby").realName("BOBBY").lastLogin(123L)
            .lastIp("12.12.12.12").build();

        boolean response = dataSource.updateSession(bobby);

        assertThat(response, equalTo(true));
        PlayerAuth result = dataSource.getAuth("bobby");
        assertThat(result, hasAuthBasicData("bobby", "BOBBY", null, "12.12.12.12"));
        assertThat(result.getLastLogin(), equalTo(123L));
    }

    @Test
    void shouldUpdateLastLoc() {
        DataSource dataSource = getDataSource();
        PlayerAuth user = PlayerAuth.builder()
            .name("user").locX(143).locY(-42.12).locZ(29.47)
            .locWorld("the_end").locYaw(2.2f).locPitch(0.45f).build();

        boolean response = dataSource.updateQuitLoc(user);

        assertThat(response, equalTo(true));
        assertThat(dataSource.getAuth("user"), hasAuthLocation(user));
    }

    @ParameterizedTest(name = "purge {0} -> {1} remaining")
    @MethodSource("purgeCases")
    void shouldPurgeRecords(Collection<String> names, int expectedRemaining) {
        DataSource dataSource = getDataSource();
        assertThat(dataSource.getAccountsRegistered(), equalTo(2)); // Make sure we start as expected

        dataSource.purgeRecords(names);

        assertThat(dataSource.getAccountsRegistered(), equalTo(expectedRemaining));
        assertThat(dataSource.getAllAuths(), hasSize(expectedRemaining));
        assertThat(dataSource.isAuthAvailable("bobby"), equalTo(false));
        assertThat(dataSource.isAuthAvailable("user"), equalTo(expectedRemaining == 1));
    }

    private static Stream<Arguments> purgeCases() {
        return Stream.of(
            Arguments.of(Arrays.asList("Bobby", "USER", "DoesnotExist"), 0),
            Arguments.of(new HashSet<>(Arrays.asList("bobby", "doesNotExist")), 1));
    }

    @Test
    void shouldUpdateEmail() {
        DataSource dataSource = getDataSource();
        String email = "new-user@mail.tld";
        PlayerAuth userAuth = PlayerAuth.builder().name("user").email(email).build();
        PlayerAuth invalidAuth = PlayerAuth.builder().name("invalid").email("addr@example.com").build();

        boolean response1 = dataSource.updateEmail(userAuth);
        boolean response2 = dataSource.updateEmail(invalidAuth);

        assertThat(response1, equalTo(true));
        assertThat(response2, equalTo(false)); // no record modified
        assertThat(dataSource.getAllAuths(), hasItem(hasAuthBasicData("user", "user", email, "34.56.78.90")));
    }

    @Test
    void shouldCountAuths() {
        DataSource dataSource = getDataSource();

        int initialCount = dataSource.getAccountsRegistered();
        for (int i = 0; i < 4; ++i) {
            dataSource.saveAuth(PlayerAuth.builder().name("test-" + i).build());
        }
        int endCount = dataSource.getAccountsRegistered();

        assertThat(initialCount, equalTo(2));
        assertThat(endCount, equalTo(6));
    }

    @Test
    void shouldGetAllUsersByIp() {
        DataSource dataSource = getDataSource();

        List<String> initialList = dataSource.getAllAuthsByIp("123.45.67.89");
        List<String> emptyList = dataSource.getAllAuthsByIp("8.8.8.8");
        for (int i = 0; i < 3; ++i) {
            PlayerAuth auth = PlayerAuth.builder().name("test-" + i).lastIp("123.45.67.89").build();
            saveAuthAndUpdateSession(dataSource, auth); // trigger storage of last IP
        }
        List<String> updatedList = dataSource.getAllAuthsByIp("123.45.67.89");

        assertThat(initialList, hasSize(1));
        assertThat(initialList.get(0), equalTo("bobby"));
        assertThat(emptyList, hasSize(0));
        assertThat(updatedList, hasSize(4));
        assertThat(updatedList, hasItem(equalTo("bobby")));
        assertThat(updatedList, hasItem(equalTo("test-1")));
    }

    @Test
    void shouldUpdateRealName() {
        DataSource dataSource = getDataSource();

        boolean response1 = dataSource.updateRealName("bobby", "BOBBY");
        boolean response2 = dataSource.updateRealName("notExists", "NOTEXISTS");

        assertThat(response1, equalTo(true));
        assertThat(response2, equalTo(false)); // no record modified
        assertThat(dataSource.getAuth("bobby"), hasAuthBasicData("bobby", "BOBBY", null, "123.45.67.89"));
    }

    @Test
    void shouldGetRecordsToPurge() {
        DataSource dataSource = getDataSource();
        // 1453242857 -> user, 1449136800 -> bobby

        PlayerAuth potato = PlayerAuth.builder().name("potato")
            .registrationDate(0L).lastLogin(1_455_000_000L).build();
        PlayerAuth tomato = PlayerAuth.builder().name("tomato")
            .registrationDate(1_457_000_000L).lastLogin(null).build();
        PlayerAuth lettuce = PlayerAuth.builder().name("Lettuce")
            .registrationDate(1_400_000_000L).lastLogin(1_453_000_000L).build();
        PlayerAuth onion = PlayerAuth.builder().name("onion")
            .registrationDate(1_200_000_000L).lastLogin(1_300_000_000L).build();
        Stream.of(potato, tomato, lettuce, onion).forEach(auth -> saveAuthAndUpdateSession(dataSource, auth));

        Set<String> records1 = dataSource.getRecordsToPurge(1_450_000_000);
        Set<String> records2 = dataSource.getRecordsToPurge(1_460_000_000);

        assertThat(records1, containsInAnyOrder("bobby", "onion"));
        assertThat(records2, containsInAnyOrder("bobby", "onion", "user", "tomato", "potato", "lettuce"));
        // check that the entry was not deleted because of running this command
        assertThat(dataSource.isAuthAvailable("bobby"), equalTo(true));
        assertThat(dataSource.isAuthAvailable("tomato"), equalTo(true));
        assertThat(dataSource.isAuthAvailable("Lettuce"), equalTo(true));
    }

    @Test
    void shouldPerformOperationsOnIsLoggedColumnSuccessfully() {
        DataSource dataSource = getDataSource();
        // on startup no one should be marked as logged
        assertThat(dataSource.isLogged("user"), equalTo(false));
        assertThat(dataSource.isLogged("bobby"), equalTo(false));

        // Mark user as logged
        dataSource.setLogged("user");
        // non-existent user should not break database
        dataSource.setLogged("does-not-exist");

        assertThat(dataSource.isLogged("user"), equalTo(true));
        assertThat(dataSource.isLogged("bobby"), equalTo(false));

        // Set bobby logged and unlog user
        dataSource.setLogged("bobby");
        dataSource.setUnlogged("user");

        assertThat(dataSource.isLogged("user"), equalTo(false));
        assertThat(dataSource.isLogged("bobby"), equalTo(true));

        // Set both as logged (even if Bobby already is logged)
        dataSource.setLogged("user");
        dataSource.setLogged("bobby");
        dataSource.purgeLogged();
        assertThat(dataSource.isLogged("user"), equalTo(false));
        assertThat(dataSource.isLogged("bobby"), equalTo(false));
    }

    @Test
    void shouldFetchEmail() {
        DataSource dataSource = getDataSource();

        DataSourceValue<String> email1 = dataSource.getEmail("user");
        DataSourceValue<String> email2 = dataSource.getEmail("Bogus");

        assertThat(email1.getValue(), equalTo("user@example.org"));
        assertThat(email2, is(DataSourceValueImpl.unknownRow()));
    }

    @Test
    void shouldGetLoggedPlayersWithoutEmail() {
        DataSource dataSource = getDataSource();
        dataSource.setLogged("bobby");
        dataSource.setLogged("user");

        List<String> loggedPlayersWithEmptyMail = dataSource.getLoggedPlayersWithEmptyMail();

        assertThat(loggedPlayersWithEmptyMail, contains("Bobby"));
    }

    @Test
    void shouldGrantAndRevokeSession() {
        DataSource dataSource = getDataSource();

        dataSource.grantSession("Bobby");
        dataSource.grantSession("doesNotExist");

        assertThat(dataSource.hasSession("bobby"), equalTo(true));
        assertThat(dataSource.hasSession("user"), equalTo(false));
        assertThat(dataSource.hasSession("bogus"), equalTo(false));

        dataSource.grantSession("user");
        dataSource.revokeSession("bobby");
        dataSource.revokeSession("userNotInDatabase");

        assertThat(dataSource.hasSession("bobby"), equalTo(false));
        assertThat(dataSource.hasSession("user"), equalTo(true));
        assertThat(dataSource.hasSession("nonExistentName"), equalTo(false));
    }

    @Test
    void shouldGetRecentlyLoggedInPlayers() {
        DataSource dataSource = getDataSource();
        String[] names = {"user3", "user8", "user2", "user4", "user7",
            "user11", "user14", "user12", "user18", "user16",
            "user28", "user29", "user22", "user20", "user24"};
        long timestamp = 1461024000; // 2016-04-19 00:00:00
        for (int i = 0; i < names.length; ++i) {
            PlayerAuth auth = PlayerAuth.builder().name(names[i])
                .registrationDate(1234567)
                .lastLogin(timestamp + i * 3600)
                .build();
            saveAuthAndUpdateSession(dataSource, auth);
        }

        List<PlayerAuth> recentPlayers = dataSource.getRecentlyLoggedInPlayers();

        assertThat(Lists.transform(recentPlayers, PlayerAuth::getNickname),
            contains("user24", "user20", "user22", "user29", "user28",
                "user16", "user18", "user12", "user14", "user11"));
    }

    @Test
    void shouldSetAndRemoveTotpKey() {
        DataSource dataSource = getDataSource();
        String newTotpKey = "My new TOTP key";

        dataSource.setTotpKey("BObBy", newTotpKey);
        dataSource.setTotpKey("does-not-exist", "bogus");
        assertThat(dataSource.getAuth("bobby").getTotpKey(), equalTo(newTotpKey));

        dataSource.removeTotpKey("BoBBy");
        dataSource.removeTotpKey("does-not-exist");
        assertThat(dataSource.getAuth("bobby").getTotpKey(), nullValue());
    }
}
