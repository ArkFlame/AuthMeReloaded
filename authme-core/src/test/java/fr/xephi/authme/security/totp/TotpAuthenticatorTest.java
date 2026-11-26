package fr.xephi.authme.security.totp;

import com.google.common.collect.Table;
import com.warrenstrange.googleauth.IGoogleAuthenticator;
import fr.xephi.authme.ReflectionTestUtils;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.security.totp.TotpAuthenticator.TotpGenerationResult;
import fr.xephi.authme.settings.Settings;
import fr.xephi.authme.settings.properties.PluginSettings;
import fr.xephi.authme.util.Utils;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static fr.xephi.authme.AuthMeMatchers.stringWithLength;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Test for {@link TotpAuthenticator}.
 */
@ExtendWith(MockitoExtension.class)
class TotpAuthenticatorTest {

    private static final String SECRET = "the_secret";
    private static final int CODE = 21398;

    private TotpAuthenticator totpAuthenticator;

    @Mock
    private Settings settings;

    @Mock
    private IGoogleAuthenticator googleAuthenticator;

    @BeforeEach
    void initializeTotpAuthenticator() {
        totpAuthenticator = new TotpAuthenticatorTestImpl(settings);
    }

    @ParameterizedTest
    @ValueSource(strings = {"MCtopia", "UT:Rebirth"})
    void shouldGenerateTotpKey(String serverName) {
        // Use the GoogleAuthenticator instance the TotpAuthenticator normally creates to test its parameters
        totpAuthenticator = new TotpAuthenticator(settings);

        Player player = mock(Player.class);
        given(player.getName()).willReturn("Bobby");
        given(settings.getProperty(PluginSettings.SERVER_NAME)).willReturn(serverName);
        String issuer = serverName.replace(':', '-');

        TotpGenerationResult key1 = totpAuthenticator.generateTotpKey(player);
        TotpGenerationResult key2 = totpAuthenticator.generateTotpKey(player);

        assertThat(key1.getTotpKey(), stringWithLength(32));
        assertThat(key2.getTotpKey(), stringWithLength(32));
        assertThat(key1.getAuthenticatorQrCodeUrl(), startsWith("https://api.qrserver.com/v1/create-qr-code/?data="));
        assertThat(key1.getAuthenticatorQrCodeUrl(), containsString(issuer));
        assertThat(key1.getAuthenticatorQrCodeUrl(), containsString("margin=10"));
        assertThat(key2.getAuthenticatorQrCodeUrl(), startsWith("https://api.qrserver.com/v1/create-qr-code/?data="));
        assertThat(key1.getTotpKey(), not(equalTo(key2.getTotpKey())));
        assertThat(key1.getAuthenticatorQrCodeUrl(), containsString(URLEncoder.encode(
            "otpauth://totp/" + issuer + ":Bobby?secret=" + key1.getTotpKey() + "&issuer=" + issuer + "&",
            StandardCharsets.UTF_8)));
    }

    @Test
    void shouldCheckCodeAndDeclareItValidOnlyOnce() {
        given(googleAuthenticator.authorize(SECRET, CODE)).willReturn(true);

        boolean result1 = totpAuthenticator.checkCode("pl", SECRET, Integer.toString(CODE));
        boolean result2 = totpAuthenticator.checkCode("pl", SECRET, Integer.toString(CODE));

        assertThat(result1, equalTo(true));
        assertThat(result2, equalTo(false));
        verify(googleAuthenticator).authorize(SECRET, CODE);
    }

    @Test
    void shouldNotBurnSetupCodeInReplayCache() {
        given(googleAuthenticator.authorize(SECRET, CODE)).willReturn(true);

        boolean setupResult = totpAuthenticator.isValidCode(SECRET, Integer.toString(CODE));
        boolean loginResult = totpAuthenticator.checkCode("pl", SECRET, Integer.toString(CODE));

        assertThat(setupResult, equalTo(true));
        assertThat(loginResult, equalTo(true));
    }

    @Test
    void shouldHandleInvalidNumberInput() {
        boolean result = totpAuthenticator.checkCode("foo", "Some_Secret", "123ZZ");

        assertThat(result, equalTo(false));
        verifyNoInteractions(googleAuthenticator);
    }

    @Test
    void shouldVerifyCode() {
        String totpKey = "ASLO43KDF2J";
        PlayerAuth auth = PlayerAuth.builder()
            .name("Maya")
            .totpKey(totpKey)
            .build();
        String inputCode = "408435";
        given(totpAuthenticator.checkCode("Maya", totpKey, inputCode)).willReturn(true);

        boolean result = totpAuthenticator.checkCode(auth, inputCode);

        assertThat(result, equalTo(true));
        verify(googleAuthenticator).authorize(totpKey, 408435);
    }

    @Test
    void shouldAcceptCodeOnlyOnceUnderConcurrentChecks() throws Exception {
        int threadCount = 8;
        CyclicBarrier ready = new CyclicBarrier(threadCount + 1);
        CountDownLatch releaseAuthorize = new CountDownLatch(1);

        given(googleAuthenticator.authorize(SECRET, CODE)).willAnswer(invocation -> {
            releaseAuthorize.await(5, TimeUnit.SECONDS);
            return true;
        });

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<Boolean>> results = new ArrayList<>();
        try {
            for (int i = 0; i < threadCount; i++) {
                results.add(executor.submit(() -> {
                    ready.await();
                    return totpAuthenticator.checkCode("pl", SECRET, Integer.toString(CODE));
                }));
            }
            ready.await();
            // Give every worker time to reach the replay check / authorize before releasing it.
            Thread.sleep(300);
            releaseAuthorize.countDown();

            int accepted = 0;
            for (Future<Boolean> result : results) {
                if (result.get(5, TimeUnit.SECONDS)) {
                    accepted++;
                }
            }
            assertThat(accepted, equalTo(1));
        } finally {
            releaseAuthorize.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void shouldRemoveOldEntries() {
        Table<String, Integer, Long> usedCodes = ReflectionTestUtils.getFieldValue(
            TotpAuthenticator.class, totpAuthenticator, "usedCodes");
        usedCodes.put("bobby", 414213, System.currentTimeMillis());
        usedCodes.put("charlie", 732050, System.currentTimeMillis() - 6 * Utils.MILLIS_PER_MINUTE);
        usedCodes.put("bobby", 236067, System.currentTimeMillis() - 9 * Utils.MILLIS_PER_MINUTE);

        totpAuthenticator.performCleanup();

        assertThat(usedCodes.size(), equalTo(1));
        assertThat(usedCodes.contains("bobby", 414213), equalTo(true));
    }

    private final class TotpAuthenticatorTestImpl extends TotpAuthenticator {

        TotpAuthenticatorTestImpl(Settings settings) {
            super(settings);
        }

        @Override
        protected IGoogleAuthenticator createGoogleAuthenticator() {
            return googleAuthenticator;
        }
    }
}
