package fr.xephi.authme.bungee.premium;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProxyPremiumLoginVerifierTest {

    private static final long TTL_MS = 60_000L;

    private final AtomicLong now = new AtomicLong(1_000L);
    private final ProxyPremiumLoginVerifier verifier =
        new ProxyPremiumLoginVerifier("test", message -> { }, now::get);

    @AfterEach
    void tearDown() {
        verifier.shutdown();
    }

    @Test
    void shouldReturnVerifiedUuidCaseInsensitivelyWithinTtl() {
        UUID uuid = UUID.randomUUID();
        verifier.storeVerified("Alice", uuid);
        now.addAndGet(TTL_MS);

        assertEquals(uuid, verifier.getVerifiedUuid("ALICE"));
    }

    @Test
    void shouldExpireVerifiedUuidAfterTtl() {
        verifier.storeVerified("alice", UUID.randomUUID());
        now.addAndGet(TTL_MS + 1);

        assertNull(verifier.getVerifiedUuid("alice"));
    }

    @Test
    void shouldSweepAbandonedSessionsWhenStoringAnother() {
        verifier.storeVerified("abandoned", UUID.randomUUID());
        now.addAndGet(TTL_MS + 1);

        // The abandoned session is evicted by the next store, not only when it is looked up again.
        verifier.storeVerified("bob", UUID.randomUUID());
        now.addAndGet(-(TTL_MS + 1)); // rewind: a surviving entry would now look fresh and be returned

        assertNull(verifier.getVerifiedUuid("abandoned"));
    }
}
