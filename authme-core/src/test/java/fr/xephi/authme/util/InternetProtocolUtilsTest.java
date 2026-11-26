package fr.xephi.authme.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * Test for {@link InternetProtocolUtils}
 */
class InternetProtocolUtilsTest {

    @ParameterizedTest
    @CsvSource({
        // loopback
        "localhost, true",
        "127.0.0.1, true",
        "::1, true",
        // site local
        "10.0.0.1, true",
        "172.0.0.1, false",
        "172.16.0.1, true",
        "192.168.0.1, true",
        // deprecated site-local, ref: https://en.wikipedia.org/wiki/IPv6_address#Default_address_selection
        "fec0::, true",
        // unique site-local (not deprecated!), ref: https://en.wikipedia.org/wiki/Unique_local_address
        "fde4:8dba:82e1::, true",
        "fc00::, true",
        "fdff:ffff:ffff:ffff:ffff:ffff:ffff:ffff, true",
        "fe00::, false",
        // link local
        "169.254.0.64, true",
        "FE80:0000:0000:0000:C800:0EFF:FE74:0008, true",
        // public
        "94.32.34.5, false",
        // IPv4 addresses starting with 252 or 253 must not be treated as IPv6 fc00::/7
        "252.0.0.1, false",
        "253.1.2.3, false"
    })
    void shouldCheckLocalAddress(String address, boolean expected) {
        assertThat(InternetProtocolUtils.isLocalAddress(address), equalTo(expected));
    }

    @Test
    void shouldRecognizeLoopbackAddresses() {
        // loopback
        assertThat(InternetProtocolUtils.isLoopbackAddress("localhost"), equalTo(true));
        assertThat(InternetProtocolUtils.isLoopbackAddress("127.0.0.1"), equalTo(true));
        assertThat(InternetProtocolUtils.isLoopbackAddress("::1"), equalTo(true));
    }
}
