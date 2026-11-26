package fr.xephi.authme.security.crypts;

import fr.xephi.authme.security.HashUtils;
import fr.xephi.authme.security.MessageDigestAlgorithm;
import fr.xephi.authme.security.crypts.description.HasSalt;
import fr.xephi.authme.security.crypts.description.Recommendation;
import fr.xephi.authme.security.crypts.description.SaltType;
import fr.xephi.authme.security.crypts.description.Usage;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

import static fr.xephi.authme.security.HashUtils.isEqual;

@Recommendation(Usage.ACCEPTABLE)
@HasSalt(value = SaltType.TEXT, length = 9)
// Note ljacqu 20151228: Wordpress is actually a salted algorithm but salt generation is handled internally
// and isn't exposed to the outside, so we treat it as an unsalted implementation
public class Wordpress extends UnsaltedMethod {

    private final SecureRandom randomGen = new SecureRandom();

    private String crypt(String password, String setting) {
        String output = "*0";
        if (((setting.length() < 2) ? setting : setting.substring(0, 2)).equalsIgnoreCase(output)) {
            output = "*1";
        }
        String id = (setting.length() < 3) ? setting : setting.substring(0, 3);
        if (!(id.equals("$P$") || id.equals("$H$"))) {
            return output;
        }
        int countLog2 = PhpassCodec.ITOA64.indexOf(setting.charAt(3));
        if (countLog2 < 7 || countLog2 > 30) {
            return output;
        }
        int count = 1 << countLog2;
        String salt = setting.substring(4, 4 + 8);
        if (salt.length() != 8) {
            return output;
        }
        MessageDigest md = HashUtils.getDigest(MessageDigestAlgorithm.MD5);
        byte[] pass = password.getBytes(StandardCharsets.UTF_8);
        byte[] hash = md.digest((salt + password).getBytes(StandardCharsets.UTF_8));
        do {
            byte[] t = new byte[hash.length + pass.length];
            System.arraycopy(hash, 0, t, 0, hash.length);
            System.arraycopy(pass, 0, t, hash.length, pass.length);
            hash = md.digest(t);
        } while (--count > 0);
        output = setting.substring(0, 12);
        output += PhpassCodec.encode64(hash, 16);
        return output;
    }

    private String gensaltPrivate(byte[] input) {
        String output = "$P$";
        int iterationCountLog2 = 8;
        output += PhpassCodec.ITOA64.charAt(Math.min(iterationCountLog2 + 5, 30));
        output += PhpassCodec.encode64(input, 6);
        return output;
    }

    @Override
    public String computeHash(String password) {
        return crypt(password, gensaltPrivate(new String(SaltBytes.random(6, randomGen))
            .getBytes(StandardCharsets.UTF_8)));
    }

    @Override
    public boolean comparePassword(String password, HashedPassword hashedPassword, String name) {
        String hash = hashedPassword.getHash();
        String comparedHash = crypt(password, hash);
        return isEqual(hash, comparedHash);
    }

}
