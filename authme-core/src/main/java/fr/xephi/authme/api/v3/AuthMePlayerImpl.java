package fr.xephi.authme.api.v3;

import fr.xephi.authme.data.auth.PlayerAuth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of {@link AuthMePlayer}. This implementation is not part of the API and
 * may have breaking changes in subsequent releases.
 */
class AuthMePlayerImpl implements AuthMePlayer {

    private final String name;
    private final UUID uuid;
    private final String email;

    private final Instant registrationDate;
    private final String registrationIpAddress;

    private final Instant lastLoginDate;
    private final String lastLoginIpAddress;

    private AuthMePlayerImpl(PlayerAuth playerAuth) {
        this.name = playerAuth.getRealName();
        this.uuid = playerAuth.getUuid();
        this.email = nullIfDefault(playerAuth.getEmail(), PlayerAuth.DB_EMAIL_DEFAULT);
        this.registrationDate = toInstant(playerAuth.getRegistrationDate());
        this.registrationIpAddress = playerAuth.getRegistrationIp();
        this.lastLoginDate = toInstant(playerAuth.getLastLogin());
        this.lastLoginIpAddress = nullIfDefault(playerAuth.getLastIp(), PlayerAuth.DB_LAST_IP_DEFAULT);
    }

    /**
     * Maps the given player auth to an AuthMePlayer instance. Returns an empty optional if
     * the player auth is null.
     *
     * @param playerAuth the player auth or null
     * @return the mapped player auth, or empty optional if the argument was null
     */
    static Optional<AuthMePlayer> fromPlayerAuth(PlayerAuth playerAuth) {
        return Optional.ofNullable(playerAuth).map(AuthMePlayerImpl::new);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Optional<UUID> getUuid() {
        return Optional.ofNullable(uuid);
    }

    @Override
    public Optional<String> getEmail() {
        return Optional.ofNullable(email);
    }

    @Override
    public Instant getRegistrationDate() {
        return registrationDate;
    }

    @Override
    public Optional<String> getRegistrationIpAddress() {
        return Optional.ofNullable(registrationIpAddress);
    }

    @Override
    public Optional<Instant> getLastLoginDate() {
        return Optional.ofNullable(lastLoginDate);
    }

    @Override
    public Optional<String> getLastLoginIpAddress() {
        return Optional.ofNullable(lastLoginIpAddress);
    }

    private static Instant toInstant(Long epochMillis) {
        return epochMillis == null ? null : Instant.ofEpochMilli(epochMillis);
    }

    private static <T> T nullIfDefault(T value, T defaultValue) {
        return defaultValue.equals(value) ? null : value;
    }
}
