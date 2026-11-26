package fr.xephi.authme.security.crypts;

import fr.xephi.authme.security.crypts.description.HasSalt;
import fr.xephi.authme.security.crypts.description.Recommendation;
import fr.xephi.authme.security.crypts.description.SaltType;
import fr.xephi.authme.security.crypts.description.Usage;
import org.bouncycastle.crypto.params.Argon2Parameters;

@Recommendation(Usage.RECOMMENDED)
@HasSalt(value = SaltType.TEXT, length = 16)
public class Argon2 extends AbstractArgon2 {

    @Override
    protected String getIdentifier() {
        return "argon2i";
    }

    @Override
    protected int getArgon2Type() {
        return Argon2Parameters.ARGON2_i;
    }
}
