package fr.xephi.authme.security.totp;

import fr.xephi.authme.initialization.HasCleanup;
import fr.xephi.authme.security.totp.TotpAuthenticator.TotpGenerationResult;
import fr.xephi.authme.util.expiring.ExpiringMap;
import org.bukkit.entity.Player;

import javax.inject.Inject;
import java.util.concurrent.TimeUnit;

/**
 * Handles the generation of new TOTP secrets for players.
 */
public class GenerateTotpService implements HasCleanup {

    private final ExpiringMap<String, TotpGenerationResult> totpKeys;

    @Inject
    private TotpAuthenticator totpAuthenticator;

    GenerateTotpService() {
        this.totpKeys = new ExpiringMap<>(5, TimeUnit.MINUTES);
    }

    /**
     * Generates a new TOTP key and returns the corresponding QR code.
     *
     * @param player the player to save the TOTP key for
     * @return TOTP generation result
     */
    public TotpGenerationResult generateTotpKey(Player player) {
        TotpGenerationResult credentials = totpAuthenticator.generateTotpKey(player);
        totpKeys.put(normalizeName(player), credentials);
        return credentials;
    }

    /**
     * Returns the generated TOTP secret of a player, if available and not yet expired.
     *
     * @param player the player to retrieve the TOTP key for
     * @return TOTP generation result
     */
    public TotpGenerationResult getGeneratedTotpKey(Player player) {
        return totpKeys.get(normalizeName(player));
    }

    public void removeGenerateTotpKey(Player player) {
        totpKeys.remove(normalizeName(player));
    }

    /**
     * Returns whether the given totp code is correct for the generated TOTP key of the player.
     *
     * @param player the player to verify the code for
     * @param totpCode the totp code to verify with the generated secret
     * @return true if the input code is correct, false if the code is invalid or no unexpired totp key is available
     */
    public boolean isTotpCodeCorrectForGeneratedTotpKey(Player player, String totpCode) {
        TotpGenerationResult totpDetails = getGeneratedTotpKey(player);
        return totpDetails != null && totpAuthenticator.isValidCode(totpDetails.getTotpKey(), totpCode);
    }

    @Override
    public void performCleanup() {
        totpKeys.removeExpiredEntries();
    }

    private static String normalizeName(Player player) {
        return TotpAuthenticator.normalizePlayerName(player.getName());
    }
}
