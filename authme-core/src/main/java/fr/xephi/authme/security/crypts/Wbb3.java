package fr.xephi.authme.security.crypts;

import fr.xephi.authme.security.crypts.description.HasSalt;
import fr.xephi.authme.security.crypts.description.Recommendation;
import fr.xephi.authme.security.crypts.description.SaltType;
import fr.xephi.authme.security.crypts.description.Usage;

import static fr.xephi.authme.security.HashUtils.sha1;

@Recommendation(Usage.ACCEPTABLE)
@HasSalt(value = SaltType.TEXT, length = 40)
public class Wbb3 extends HexSeparateSaltMethod {

    @Override
    public String computeHash(String password, String salt, String name) {
        return sha1(salt.concat(sha1(salt.concat(sha1(password)))));
    }

    @Override
    public int getSaltLength() {
        return 40;
    }
}
