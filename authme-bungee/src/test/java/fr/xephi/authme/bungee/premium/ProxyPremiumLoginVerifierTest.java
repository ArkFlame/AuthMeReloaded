package fr.xephi.authme.bungee.premium;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;

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

    @Test
    void shouldRejectCompletionOfHandshakeOlderThanPendingTtl() throws Exception {
        java.util.List<String> warnings = new java.util.concurrent.CopyOnWriteArrayList<>();
        ProxyPremiumLoginVerifier logging = new ProxyPremiumLoginVerifier("test-ttl", warnings::add, now::get);
        try {
            // Control: a fresh handshake reaches the verification step, which fails on the garbage token and warns.
            logging.startVerification("fresh", "alice", UUID.randomUUID(), false);
            assertFalse(logging.completeVerification("fresh", new byte[16], new byte[16]).get().isPresent());
            assertEquals(1, warnings.size());

            // An expired handshake is rejected before any decryption or lookup, so nothing is logged.
            logging.startVerification("stale", "bob", UUID.randomUUID(), false);
            now.addAndGet(30_001L);
            assertFalse(logging.completeVerification("stale", new byte[16], new byte[16]).get().isPresent());
            assertEquals(1, warnings.size());
        } finally {
            logging.shutdown();
        }
    }
}
