package fr.xephi.authme.security.totp;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.common.primitives.Ints;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import com.warrenstrange.googleauth.GoogleAuthenticatorQRGenerator;
import com.warrenstrange.googleauth.IGoogleAuthenticator;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.initialization.HasCleanup;
import fr.xephi.authme.settings.Settings;
import fr.xephi.authme.settings.properties.PluginSettings;
import org.bukkit.entity.Player;

import javax.inject.Inject;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import static fr.xephi.authme.util.Utils.MILLIS_PER_MINUTE;

/**
 * Provides TOTP functions (wrapping a third-party TOTP implementation).
 */
public class TotpAuthenticator implements HasCleanup {

    private final IGoogleAuthenticator authenticator;
    private final Settings settings;
    // All access (contains/put and cleanup) is guarded by this table's monitor.
    private final Table<String, Integer, Long> usedCodes = HashBasedTable.create();

    @Inject
    TotpAuthenticator(Settings settings) {
        this.authenticator = createGoogleAuthenticator();
        this.settings = settings;
    }

    /**
     * @return new Google Authenticator instance
     */
    protected IGoogleAuthenticator createGoogleAuthenticator() {
        return new GoogleAuthenticator();
    }

    /**
     * Normalizes a player name for use as a key in the TOTP caches.
     *
     * @param playerName the player name
     * @return the name in lowercase, using the root locale
     */
    static String normalizePlayerName(String playerName) {
        return playerName.toLowerCase(Locale.ROOT);
    }

    public boolean checkCode(PlayerAuth auth, String totpCode) {
        return checkCode(auth.getNickname(), auth.getTotpKey(), totpCode);
    }

    /**
     * Returns whether the given input code matches for the provided TOTP key.
     *
     * @param playerName the player name
     * @param totpKey the key to check with
     * @param inputCode the input code to verify
     * @return true if code is valid, false otherwise
     */
    public boolean checkCode(String playerName, String totpKey, String inputCode) {
        String nameLower = normalizePlayerName(playerName);
        Integer totpCode = Ints.tryParse(inputCode);
        // Keep the replay decision and the write atomic: a concurrent checkCode call must not
        // be able to accept the same still-unrecorded code, and cleanup must not purge mid-check.
        synchronized (usedCodes) {
            if (totpCode != null && !usedCodes.contains(nameLower, totpCode)
                && authenticator.authorize(totpKey, totpCode)) {
                usedCodes.put(nameLower, totpCode, System.currentTimeMillis());
                return true;
            }
            return false;
        }
    }

    /**
     * Verifies a code without recording it in the login replay cache. Used when confirming a newly
     * generated (not yet installed) secret, so the same still-valid code remains usable at login.
     *
     * @param totpKey the key to check with
     * @param inputCode the input code to verify
     * @return true if code is valid, false otherwise
     */
    public boolean isValidCode(String totpKey, String inputCode) {
        Integer totpCode = Ints.tryParse(inputCode);
        return totpCode != null && authenticator.authorize(totpKey, totpCode);
    }

    public TotpGenerationResult generateTotpKey(Player player) {
        GoogleAuthenticatorKey credentials = authenticator.createCredentials();
        // The otpauth label is "issuer:account", so the issuer must not contain a colon
        String issuer = settings.getProperty(PluginSettings.SERVER_NAME).replace(':', '-');
        String otpAuthUrl = GoogleAuthenticatorQRGenerator.getOtpAuthTotpURL(issuer, player.getName(), credentials);
        try {
            return new TotpGenerationResult(credentials.getKey(),
                "https://api.qrserver.com/v1/create-qr-code/?data="
                    + URLEncoder.encode(otpAuthUrl, StandardCharsets.UTF_8.name())
                    + "&size=200x200&ecc=M&margin=10");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 encoding not supported", e);
        }
    }

    @Override
    public void performCleanup() {
        long threshold = System.currentTimeMillis() - 5 * MILLIS_PER_MINUTE;
        synchronized (usedCodes) {
            usedCodes.values().removeIf(value -> value < threshold);
        }
    }

    public static final class TotpGenerationResult {
        private final String totpKey;
        private final String authenticatorQrCodeUrl;

        public TotpGenerationResult(String totpKey, String authenticatorQrCodeUrl) {
            this.totpKey = totpKey;
            this.authenticatorQrCodeUrl = authenticatorQrCodeUrl;
        }

        public String getTotpKey() {
            return totpKey;
        }

        public String getAuthenticatorQrCodeUrl() {
            return authenticatorQrCodeUrl;
        }
    }
}
