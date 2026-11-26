package fr.xephi.authme.service;

import fr.xephi.authme.ConsoleLogger;
import fr.xephi.authme.data.ProxySessionManager;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.output.ConsoleLoggerFactory;
import fr.xephi.authme.process.SyncProcessManager;
import fr.xephi.authme.process.login.AsynchronousLogin;
import fr.xephi.authme.security.PasswordSecurity;
import fr.xephi.authme.service.bungeecord.BungeeSender;
import fr.xephi.authme.settings.Settings;
import fr.xephi.authme.settings.properties.PremiumSettings;
import fr.xephi.authme.util.PlayerUtils;
import org.bukkit.entity.Player;

import javax.inject.Inject;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Optionally creates an account for a joining player whose premium identity has already been
 * verified by the same checks used for premium bypass.
 *
 * <p>Only players without an account are considered. The password is random and is never shown
 * to the player, so the account can only be used through premium verification.</p>
 */
public class PremiumAutoRegisterService {

    private final ConsoleLogger logger = ConsoleLoggerFactory.get(PremiumAutoRegisterService.class);
    private final Set<String> registering = ConcurrentHashMap.newKeySet();

    @Inject
    private Settings settings;

    @Inject
    private DataSource dataSource;

    @Inject
    private BukkitService bukkitService;

    @Inject
    private PremiumLoginVerifier premiumLoginVerifier;

    @Inject
    private ProxySessionManager proxySessionManager;

    @Inject
    private BungeeSender bungeeSender;

    @Inject
    private PasswordSecurity passwordSecurity;

    @Inject
    private AsynchronousLogin asynchronousLogin;

    @Inject
    private SyncProcessManager syncProcessManager;

    PremiumAutoRegisterService() {
    }

    /**
     * Checks the opt-in setting and the player's premium identity, then schedules creation of
     * the account. Returns false whenever the join flow must continue as usual.
     *
     * @param player the joining player
     * @return true if an auto-registration task was scheduled, false otherwise
     */
    public boolean tryAutoRegister(Player player) {
        if (!settings.getProperty(PremiumSettings.AUTO_REGISTER)
                || !settings.getProperty(PremiumSettings.ENABLE_PREMIUM)) {
            return false;
        }

        String name = player.getName();
        if (name == null || dataSource.isAuthAvailable(name.toLowerCase(Locale.ROOT))) {
            return false;
        }

        UUID premiumUuid = resolveVerifiedPremiumUuid(player, name, false);
        if (premiumUuid == null) {
            return false;
        }

        bukkitService.runTaskOptionallyAsync(() -> createAccount(player, premiumUuid));
        return true;
    }

    /**
     * Resolves the verified premium UUID from exactly the same identity sources as premium bypass:
     * a Mojang UUID v4 from the handshake/proxy, a signed proxy premium claim, or a
     * PacketEvents-verified session. A player name on its own is never sufficient.
     * The PacketEvents source is consumed when {@code consume} is set, so a session cannot be
     * reused after it has been checked again in the saving task.
     */
    private UUID resolveVerifiedPremiumUuid(Player player, String name, boolean consume) {
        UUID playerUuid = player.getUniqueId();
        if (playerUuid != null && playerUuid.version() == 4) {
            return playerUuid;
        }
        if (bungeeSender.isEnabled()) {
            ProxySessionManager.ProxyLoginRequest request = proxySessionManager.getLoginRequest(name);
            return request == null ? null : request.verifiedPremiumUuid();
        }
        return consume
            ? premiumLoginVerifier.consumeVerifiedUuid(name)
            : premiumLoginVerifier.getVerifiedUuid(name);
    }

    private void createAccount(Player player, UUID premiumUuid) {
        String name = player.getName();
        String normalizedName = name.toLowerCase(Locale.ROOT);
        if (!registering.add(normalizedName)) {
            return;
        }
        try {
            // Re-check inside the saving task so an expired or consumed session cannot register.
            UUID verifiedUuid = resolveVerifiedPremiumUuid(player, name, true);
            if (!premiumUuid.equals(verifiedUuid)) {
                return;
            }

            String password = UUID.randomUUID().toString() + UUID.randomUUID().toString();
            PlayerAuth auth = PlayerAuth.builder()
                .name(normalizedName)
                .realName(name)
                .password(passwordSecurity.computeHash(password, normalizedName))
                .registrationIp(PlayerUtils.getPlayerIp(player))
                .registrationDate(System.currentTimeMillis())
                .uuid(player.getUniqueId())
                .premiumUuid(verifiedUuid)
                .build();

            // Final availability check in the same task that saves, immediately before the insert.
            if (dataSource.isAuthAvailable(normalizedName)) {
                return;
            }
            try {
                if (!dataSource.saveAuth(auth)) {
                    // Another registration won the race; leave its account untouched.
                    logger.fine("Premium auto-registration for " + name + " was already registered");
                    return;
                }
            } catch (RuntimeException e) {
                // SQL duplicate key on the name column: the account already exists.
                logger.fine("Premium auto-registration for " + name + " raced with an existing account");
                return;
            }

            if (!dataSource.updatePremiumUuid(auth)) {
                logger.warning("Failed to mark auto-registered player " + name + " as premium");
            }
            bungeeSender.sendPremiumSet(name);

            // Same events as a regular registration followed by a password-less login.
            bukkitService.runTaskOptionallyAsync(() -> asynchronousLogin.forceLogin(player));
            syncProcessManager.processSyncPasswordRegister(player);
        } finally {
            registering.remove(normalizedName);
        }
    }

}
