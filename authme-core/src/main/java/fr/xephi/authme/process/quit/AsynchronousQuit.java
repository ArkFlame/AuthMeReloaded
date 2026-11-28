package fr.xephi.authme.process.quit;

import fr.xephi.authme.AuthMe;
import fr.xephi.authme.ConsoleLogger;
import fr.xephi.authme.data.ProxySessionManager;
import fr.xephi.authme.data.VerificationCodeManager;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.data.auth.PlayerCache;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.output.ConsoleLoggerFactory;
import fr.xephi.authme.process.AsynchronousProcess;
import fr.xephi.authme.process.SyncProcessManager;
import fr.xephi.authme.service.CommonService;
import fr.xephi.authme.service.DialogStateService;
import fr.xephi.authme.service.PremiumLoginVerifier;
import fr.xephi.authme.service.SessionService;
import fr.xephi.authme.service.TeleportationService;
import fr.xephi.authme.service.ValidationService;
import fr.xephi.authme.settings.SpawnLoader;
import fr.xephi.authme.settings.properties.PluginSettings;
import fr.xephi.authme.settings.properties.RestrictionSettings;
import fr.xephi.authme.util.PlayerUtils;
import org.bukkit.entity.Player;

import javax.inject.Inject;
import java.util.Locale;

/**
 * Async process called when a player quits the server.
 */
public class AsynchronousQuit implements AsynchronousProcess {

    private final ConsoleLogger logger = ConsoleLoggerFactory.get(AsynchronousQuit.class);

    @Inject
    private AuthMe plugin;

    @Inject
    private DataSource database;

    @Inject
    private CommonService service;

    @Inject
    private PlayerCache playerCache;

    @Inject
    private SyncProcessManager syncProcessManager;

    @Inject
    private SpawnLoader spawnLoader;

    @Inject
    private ValidationService validationService;

    @Inject
    private VerificationCodeManager codeManager;

    @Inject
    private SessionService sessionService;

    @Inject
    private TeleportationService teleportationService;

    @Inject
    private DialogStateService dialogStateService;

    @Inject
    private ProxySessionManager proxySessionManager;

    @Inject
    private PremiumLoginVerifier premiumLoginVerifier;

    AsynchronousQuit() {
    }

    /**
     * Processes that the given player has quit the server.
     *
     * @param player the player who left
     */
    public void processQuit(Player player) {
        if (player == null) {
            return;
        }
        String realName = player.getName();
        if (validationService.isUnrestricted(realName)) {
            return;
        }
        String name = realName.toLowerCase(Locale.ROOT);
        teleportationService.clearOriginalJoinLocation(name);
        dialogStateService.clearDialogOpen(player);
        proxySessionManager.removeLoginRequest(name);
        premiumLoginVerifier.removeVerified(name);
        boolean wasLoggedIn = playerCache.isAuthenticated(name);

        try {
            if (wasLoggedIn) {
                // One auth for both updates: neither datasource method reads the other's columns.
                PlayerAuth auth = PlayerAuth.builder().name(name).realName(realName)
                    .lastIp(quitIp(player, name)).lastLogin(System.currentTimeMillis()).build();
                if (service.getProperty(RestrictionSettings.SAVE_QUIT_LOCATION)) {
                    try {
                        auth.setQuitLocation(spawnLoader.getPlayerLocationOrSpawn(player));
                        database.updateQuitLoc(auth);
                    } catch (RuntimeException e) {
                        logger.warning("Could not save the quit location of '" + name + "': " + e.getMessage());
                    }
                }
                database.updateSession(auth);
            }
        } finally {
            //always unauthenticate the player - use session only for auto logins on the same ip
            playerCache.removePlayer(name);
            codeManager.unverify(name);

            //always update the database when the player quit the game (if sessions are disabled)
            if (wasLoggedIn) {
                database.setUnlogged(name);
                if (!service.getProperty(PluginSettings.SESSIONS_ENABLED)) {
                    sessionService.revokeSession(name);
                }
            }

            if (plugin.isEnabled()) {
                syncProcessManager.processSyncPlayerQuit(player, wasLoggedIn);
            }

            database.invalidateCache(name);
        }
    }

    /** The address can already be gone at quit time: keep the IP the player logged in with instead of failing. */
    private String quitIp(Player player, String name) {
        if (player.getAddress() != null && player.getAddress().getAddress() != null) {
            return PlayerUtils.getPlayerIp(player);
        }
        PlayerAuth cached = playerCache.getAuth(name);
        return cached == null ? null : cached.getLastIp();
    }

}
