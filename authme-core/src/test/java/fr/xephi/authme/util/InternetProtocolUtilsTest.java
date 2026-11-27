package fr.xephi.authme.util;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * Test for {@link InternetProtocolUtils}
 */
class InternetProtocolUtilsTest {

    @Test
    void shouldRecognizeLoopbackAddresses() {
        // loopback
        assertThat(InternetProtocolUtils.isLoopbackAddress("localhost"), equalTo(true));
        assertThat(InternetProtocolUtils.isLoopbackAddress("127.0.0.1"), equalTo(true));
        assertThat(InternetProtocolUtils.isLoopbackAddress("::1"), equalTo(true));
    }
}
