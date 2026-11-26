package fr.xephi.authme.velocity.premium;

import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.player.GameProfileRequestEvent;
import com.velocitypowered.api.proxy.InboundConnection;
import com.velocitypowered.api.util.GameProfile;
import com.velocitypowered.api.util.UuidUtils;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.helpers.NOPLogger;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class VelocityPremiumVerificationManagerTest {

    private static final InboundConnection CONNECTION = mock(InboundConnection.class);
    private static final UUID MOJANG_UUID = UUID.fromString("8d6d0684-d8b4-4d40-8d2d-0dd4df5555c8");
    private static final Predicate<String> IS_ALICE = "alice"::equals;
    private static final Predicate<String> NEVER = name -> false;
    private static final Consumer<String> NOOP = name -> { };
    private static final BooleanSupplier OFFLINE_COMPAT_DISABLED = () -> false;

    private static VelocityPremiumVerificationManager manager(
            Predicate<String> requiresVerification, Predicate<String> isPendingVerification,
            Consumer<String> failureHandler, BooleanSupplier offlineCompatibility,
            Predicate<String> isNameConnected) {
        return new VelocityPremiumVerificationManager(NOPLogger.NOP_LOGGER, requiresVerification,
            isPendingVerification, failureHandler, offlineCompatibility, isNameConnected);
    }

    private static VelocityPremiumVerificationManager manager() {
        return manager(IS_ALICE, NEVER, NOOP, OFFLINE_COMPAT_DISABLED, NEVER);
    }

    private static VelocityPremiumVerificationManager pendingManager(
            Predicate<String> requiresVerification, Set<String> pending) {
        return manager(requiresVerification, pending::contains, pending::remove,
            OFFLINE_COMPAT_DISABLED, NEVER);
    }

    @Test
    void shouldForceOnlineModeForPremiumUser() {
        VelocityPremiumVerificationManager manager = manager();

        PreLoginEvent event = new PreLoginEvent(CONNECTION, "Alice", null);
        manager.onPreLogin(event);

        assertEquals(PreLoginEvent.PreLoginComponentResult.forceOnlineMode().toString(),
            event.getResult().toString());
    }

    @Test
    void shouldNotOverrideEarlierDenialWhenForcingOnlineMode() {
        VelocityPremiumVerificationManager manager = manager();
        PreLoginEvent event = new PreLoginEvent(CONNECTION, "Alice", null);
        event.setResult(PreLoginEvent.PreLoginComponentResult.denied(Component.text("Banned")));

        manager.onPreLogin(event);

        assertFalse(event.getResult().isAllowed());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldRewriteVerifiedProfileToOfflineUuidWhenOfflineCompatibilityEnabled(boolean offlineCompatibility) {
        VelocityPremiumVerificationManager manager =
            manager(IS_ALICE, NEVER, NOOP, () -> offlineCompatibility, NEVER);
        GameProfileRequestEvent event = new GameProfileRequestEvent(
            CONNECTION, new GameProfile(MOJANG_UUID, "Alice", List.of()), true);

        manager.onGameProfileRequest(event);

        UUID expectedUuid = offlineCompatibility ? UuidUtils.generateOfflinePlayerUuid("Alice") : MOJANG_UUID;
        assertEquals(expectedUuid, event.getGameProfile().getId());
        assertEquals(MOJANG_UUID, manager.getVerifiedPremiumUuid("alice"));
    }

    @Test
    void shouldNotStoreVerificationWhenPlayerWithNameIsAlreadyConnected() {
        VelocityPremiumVerificationManager manager =
            manager(IS_ALICE, NEVER, NOOP, OFFLINE_COMPAT_DISABLED, IS_ALICE);
        GameProfileRequestEvent event = new GameProfileRequestEvent(
            CONNECTION, new GameProfile(MOJANG_UUID, "Alice", List.of()), true);

        manager.onGameProfileRequest(event);

        assertNull(manager.getVerifiedPremiumUuid("alice"));
    }

    @Test
    void shouldIgnoreOfflineModeProfileRequest() {
        VelocityPremiumVerificationManager manager = manager(IS_ALICE, NEVER, NOOP, () -> true, NEVER);
        GameProfileRequestEvent event = new GameProfileRequestEvent(
            CONNECTION, new GameProfile(MOJANG_UUID, "Alice", List.of()), false);

        manager.onGameProfileRequest(event);

        assertEquals(MOJANG_UUID, event.getGameProfile().getId());
        assertNull(manager.getVerifiedPremiumUuid("alice"));
    }

    @Test
    void shouldForceOnlineModeOnFirstPendingAttemptThenCancelOnSecond() {
        Set<String> pending = new HashSet<>(Set.of("alice"));
        VelocityPremiumVerificationManager manager = pendingManager(pending::contains, pending);

        PreLoginEvent firstAttempt = new PreLoginEvent(CONNECTION, "Alice", null);
        manager.onPreLogin(firstAttempt);
        // Mojang rejected the session: no profile request, no login and no disconnect event follow
        PreLoginEvent secondAttempt = new PreLoginEvent(CONNECTION, "Alice", null);
        manager.onPreLogin(secondAttempt);

        assertEquals(PreLoginEvent.PreLoginComponentResult.forceOnlineMode().toString(),
            firstAttempt.getResult().toString());
        assertEquals(PreLoginEvent.PreLoginComponentResult.allowed().toString(),
            secondAttempt.getResult().toString());
        assertFalse(pending.contains("alice"));
    }

    @Test
    void shouldKeepForcingOnlineModeForPendingPlayerAfterSuccessfulVerification() {
        Set<String> pending = new HashSet<>(Set.of("alice"));
        VelocityPremiumVerificationManager manager = pendingManager(pending::contains, pending);
        GameProfile profile = new GameProfile(MOJANG_UUID, "Alice", List.of());

        manager.onPreLogin(new PreLoginEvent(CONNECTION, "Alice", null));
        manager.onGameProfileRequest(new GameProfileRequestEvent(CONNECTION, profile, true));
        // The backend has not finalized the enrollment yet, so the player is still pending on reconnect
        PreLoginEvent reconnect = new PreLoginEvent(CONNECTION, "Alice", null);
        manager.onPreLogin(reconnect);

        assertEquals(PreLoginEvent.PreLoginComponentResult.forceOnlineMode().toString(),
            reconnect.getResult().toString());
        assertTrue(pending.contains("alice"));
    }

    @Test
    void shouldStillForceOnlineModeForEnrolledPremiumPlayerWhenPendingAttemptFails() {
        Set<String> pending = new HashSet<>(Set.of("alice"));
        Predicate<String> requiresVerification = name -> "alice".equals(name) || pending.contains(name);
        VelocityPremiumVerificationManager manager = pendingManager(requiresVerification, pending);

        manager.onPreLogin(new PreLoginEvent(CONNECTION, "Alice", null));
        PreLoginEvent secondAttempt = new PreLoginEvent(CONNECTION, "Alice", null);
        manager.onPreLogin(secondAttempt);

        assertEquals(PreLoginEvent.PreLoginComponentResult.forceOnlineMode().toString(),
            secondAttempt.getResult().toString());
        assertFalse(pending.contains("alice"));
    }

    @Test
    void shouldResetAttemptWhenPendingVerificationIsRearmed() {
        Set<String> pending = new HashSet<>(Set.of("alice"));
        VelocityPremiumVerificationManager manager = pendingManager(pending::contains, pending);

        manager.onPreLogin(new PreLoginEvent(CONNECTION, "Alice", null));
        // A new /premium run sends premium.pending.set again, which clears the verification state
        manager.clearVerifiedPremium("alice");
        PreLoginEvent reconnect = new PreLoginEvent(CONNECTION, "Alice", null);
        manager.onPreLogin(reconnect);

        assertEquals(PreLoginEvent.PreLoginComponentResult.forceOnlineMode().toString(),
            reconnect.getResult().toString());
        assertTrue(pending.contains("alice"));
    }
}
