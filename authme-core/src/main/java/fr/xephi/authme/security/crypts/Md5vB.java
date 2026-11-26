package fr.xephi.authme.security.crypts;

import static fr.xephi.authme.security.HashUtils.md5;

public class Md5vB extends HexSaltedMethod {

    @Override
    public String computeHash(String password, String salt, String name) {
        return "$MD5vb$" + salt + "$" + md5(md5(password) + salt);
    }

    @Override
    public boolean comparePassword(String password, HashedPassword hashedPassword, String name) {
        return comparePrefixedHash(password, hashedPassword, name);
    }

    @Override
    public int getSaltLength() {
        return 16;
    }

}
