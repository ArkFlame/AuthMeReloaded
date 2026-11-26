package fr.xephi.authme.security.crypts;

import fr.xephi.authme.security.HashUtils;
import fr.xephi.authme.security.MessageDigestAlgorithm;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class CrazyCrypt1 extends UsernameSaltMethod {

    @Override
    public HashedPassword computeHash(String password, String name) {
        final String text = "ÜÄaeut//&/=I " + password + "7421€547" + name + "__+IÄIH§%NK " + password;
        final MessageDigest md = HashUtils.getDigest(MessageDigestAlgorithm.SHA512);
        md.update(text.getBytes(StandardCharsets.UTF_8), 0, text.length());
        return new HashedPassword(HashUtils.toHex(md.digest()));
    }

}
