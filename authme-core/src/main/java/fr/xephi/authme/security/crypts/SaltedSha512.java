package fr.xephi.authme.security.crypts;

import fr.xephi.authme.security.HashUtils;
import fr.xephi.authme.security.crypts.description.Recommendation;
import fr.xephi.authme.security.crypts.description.Usage;

@Recommendation(Usage.RECOMMENDED)
public class SaltedSha512 extends HexSeparateSaltMethod {

    @Override
    public String computeHash(String password, String salt, String name) {
        return HashUtils.sha512(password + salt);
    }

    @Override
    public int getSaltLength() {
        return 32;
    }
}
