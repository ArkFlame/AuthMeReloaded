package fr.xephi.authme.security.crypts;

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Test for {@link PhpBB}.
 */
class PhpBBTest extends AbstractEncryptionMethodTest {

    PhpBBTest() {
        super(new PhpBB(),
            "$2a$10$1rnuna3GBduBy1NQuOpnWODqBfl8CZHeULuBThNfAvkOYDRRQR1Zi",  // password
            "$2a$10$F6LVgXa8.t95H0Fikr6nG.aEMgIQRXlFpzMvAjbO7ag3fny9GGS3i",  // PassWord1
            "$2a$10$ex57hkfuMLwYsdG8ru/4teh48kHCSv0HPLPjhhHsEB3NqXiOi7RQS",  // &^%te$t?Pw@_
            "$2a$10$2B/HAJ3MeoxGQgqLM6GDlOBqd.2uzLPi1VznXlrXcayLixSaRIWqC"); // âË_3(íù*
    }

    @ParameterizedTest(name = "match hashes of type {0}")
    @MethodSource("hashSets")
    void shouldMatchGivenHashes(String hashType, Map<String, String> givenHashes) {
        PhpBB phpBB = new PhpBB();

        for (Map.Entry<String, String> hashEntry : givenHashes.entrySet()) {
            if (!phpBB.comparePassword(hashEntry.getKey(), new HashedPassword(hashEntry.getValue()), null)) {
                fail("Hash comparison for '" + hashEntry.getKey() + "' failed");
            }
        }
    }

    static Stream<Arguments> hashSets() {
        return Stream.of(
            Arguments.of("phpass salted MD5", ImmutableMap.of(
                "password", "$H$7MaSGQb0xe3Fp/a.Q.Ewpw.UKfCv.t0",
                "PassWord1", "$H$7ESfAVjzqajC7fJFcZKZIhyds41MuW.",
                "&^%te$t?Pw@_", "$H$7G65SXRPbR69jLg.qZTjtqsw36Ciw7.",
                "âË_3(íù*", "$H$7Brcg8zO9amr2SHVgz.pFxprDu40v4/")),
            Arguments.of("unsalted MD5", ImmutableMap.of(
                "password", "5f4dcc3b5aa765d61d8327deb882cf99",
                "PassWord1", "f2126d405f46ed603ff5b2950f062c96",
                "&^%te$t?Pw@_", "0833dcd2bc741f90c46bbac5498fd08f",
                "âË_3(íù*", "e7412bf1a9d312dc2901c3101a097abe")));
    }

}
