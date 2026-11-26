package fr.xephi.authme.security.crypts;

import fr.xephi.authme.security.HashUtils;
import fr.xephi.authme.security.crypts.description.AsciiRestricted;
import fr.xephi.authme.security.crypts.description.Recommendation;
import fr.xephi.authme.security.crypts.description.Usage;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

@Recommendation(Usage.DO_NOT_USE)
@AsciiRestricted
public class PhpFusion extends HexSeparateSaltMethod {

    @Override
    public String computeHash(String password, String salt, String name) {
        String algo = "HmacSHA256";
        String keyString = HashUtils.sha1(salt);
        try {
            byte[] bytes = HashUtils.createMac(algo, keyString.getBytes(StandardCharsets.UTF_8))
                .doFinal(password.getBytes(StandardCharsets.US_ASCII));
            return HashUtils.toHex(bytes);
        } catch (InvalidKeyException | NoSuchAlgorithmException e) {
            throw new UnsupportedOperationException("Cannot create PHPFUSION hash for " + name, e);
        }
    }

    @Override
    public int getSaltLength() {
        return 12;
    }
}
