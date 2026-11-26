package fr.xephi.authme.listener;

import com.destroystokyo.paper.event.player.PlayerConnectionCloseEvent;
import com.destroystokyo.paper.profile.PlayerProfile;
import fr.xephi.authme.data.ProxySessionManager;
import fr.xephi.authme.data.TempbanManager;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.data.auth.PlayerCache;
import fr.xephi.authme.data.captcha.LoginCaptchaManager;
import fr.xephi.authme.data.captcha.RegistrationCaptchaManager;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.events.PreJoinDialogEvent;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.message.Messages;
import fr.xephi.authme.platform.PaperDialogActionKeys;
import fr.xephi.authme.platform.PaperDialogHelper;
import fr.xephi.authme.process.register.RegisterSecondaryArgument;
import fr.xephi.authme.process.register.RegistrationType;
import fr.xephi.authme.security.PasswordSecurity;
import fr.xephi.authme.service.BukkitService;
import fr.xephi.authme.service.CommonService;
import fr.xephi.authme.service.DialogWindowService;
import fr.xephi.authme.service.FloodgateService;
import fr.xephi.authme.service.PendingPremiumCache;
import fr.xephi.authme.service.PreJoinDialogService;
import fr.xephi.authme.service.PremiumLoginVerifier;
import fr.xephi.authme.service.SessionService;
import fr.xephi.authme.service.ValidationService;
import fr.xephi.authme.settings.properties.PremiumSettings;
import fr.xephi.authme.settings.properties.RegistrationSettings;
import fr.xephi.authme.settings.properties.RestrictionSettings;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.event.connection.configuration.AsyncPlayerConnectionConfigureEvent;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import javax.inject.Inject;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * Handles Paper/Folia dialog flows that happen during the configuration phase.
 *
 * <p>This class owns the connection/session state and the event handlers; dialog eligibility, event
 * dispatch, and the login/registration answer processing live in focused collaborators in this package.
 */
public class PaperDialogFlowListener implements Listener {

    private final ConcurrentMap<Long, CompletableFuture<String>> pendingLoginResponses = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, CompletableFuture<String>> pendingRegisterResponses = new ConcurrentHashMap<>();
    private final ConcurrentMap<PlayerConfigurationConnection, Long> connectionSessions = new ConcurrentHashMap<>();

    @Inject
    private CommonService commonService;

    @Inject
    private DataSource dataSource;

    @Inject
    private Messages messages;

    @Inject
    private PasswordSecurity passwordSecurity;

    @Inject
    private ValidationService validationService;

    @Inject
    private PlayerCache playerCache;

    @Inject
    private PreJoinDialogService preJoinDialogService;

    @Inject
    private PendingPremiumCache pendingPremiumCache;

    @Inject
    private DialogWindowService dialogWindowService;

    @Inject
    private SessionService sessionService;

    @Inject
    private ProxySessionManager proxySessionManager;

    @Inject
    private PremiumLoginVerifier premiumLoginVerifier;

    @Inject
    private FloodgateService floodgateService;

    @Inject
    private LoginCaptchaManager loginCaptchaManager;

    @Inject
    private RegistrationCaptchaManager registrationCaptchaManager;

    @Inject
    private TempbanManager tempbanManager;

    @Inject
    private BukkitService bukkitService;

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerConfigure(AsyncPlayerConnectionConfigureEvent event) {
        if (!commonService.getProperty(RegistrationSettings.USE_PREJOIN_DIALOG_UI)) {
            return;
        }

        PlayerConfigurationConnection connection = event.getConnection();
        PlayerProfile profile = connection.getProfile();
        UUID playerId = profile.getId();
        String playerName = profile.getName();
        if (playerId == null || playerName == null) {
            return;
        }

        retireSession(connection);

        String normalizedName = playerName.toLowerCase(Locale.ROOT);
        Set<String> unrestrictedNames = commonService.getProperty(RestrictionSettings.UNRESTRICTED_NAMES);
        if (unrestrictedNames.contains(normalizedName)) {
            return;
        }
        if (PreJoinDialogGate.shouldSkipDialogs(normalizedName, connection, playerCache, proxySessionManager,
            sessionService, dataSource, pendingPremiumCache)) {
            return;
        }

        long sessionId = preJoinDialogService.openSession(normalizedName);
        connectionSessions.put(connection, sessionId);

        PlayerAuth auth = dataSource.getAuth(normalizedName);
        // Bedrock players (Floodgate) are logged in or registered on join, without a password
        if (auth != null ? floodgateService.canAutoLogin(playerId) : floodgateService.canAutoRegister(playerId)) {
            preJoinDialogService.markSkipPostJoinDialog(sessionId);
            return;
        }
        if (auth != null) {
            if (shouldSkipPreJoinDialogForPremium(auth, playerName, playerId)) {
                preJoinDialogService.markSkipPostJoinDialog(sessionId);
                return;
            }
            handleBlockingLoginDialog(connection, sessionId, playerName);
        } else if (commonService.getProperty(RegistrationSettings.FORCE)) {
            RegistrationType registrationType = commonService.getProperty(RegistrationSettings.REGISTRATION_TYPE);
            RegisterSecondaryArgument secondArg =
                commonService.getProperty(RegistrationSettings.REGISTER_SECOND_ARGUMENT);
            handleBlockingRegisterDialog(connection, sessionId, playerName, PaperDialogHelper.createPreJoinRegisterDialog(
                dialogWindowService.createPreJoinRegisterDialog(playerName, registrationType, secondArg)));
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerCustomClick(PlayerCustomClickEvent event) {
        if (!(event.getCommonConnection() instanceof PlayerConfigurationConnection connection)) {
            return;
        }

        String playerName = connection.getProfile().getName();
        Long sessionId = connectionSessions.get(connection);
        if (playerName == null || sessionId == null) {
            return;
        }

        var identifier = event.getIdentifier();
        if (PaperDialogActionKeys.PRE_JOIN_LOGIN_CANCEL.equals(identifier)
            || PaperDialogActionKeys.PRE_JOIN_RECOVERY_CANCEL.equals(identifier)) {
            String kickMessage = commonService.getProperty(RegistrationSettings.PRE_JOIN_LOGIN_CANCEL_KICKS)
                ? messages.retrieveSingle(playerName, MessageKey.DIALOG_LOGIN_CANCELED)
                : null;
            PaperDialogSessionSupport.completeResponse(pendingLoginResponses, sessionId, kickMessage);
            return;
        }

        if (PaperDialogActionKeys.PRE_JOIN_LOGIN_RECOVERY.equals(identifier)) {
            loginProcessor().showRecovery(playerName, connection);
            return;
        }

        if (PaperDialogActionKeys.PRE_JOIN_RECOVERY_SUBMIT.equals(identifier)) {
            loginProcessor().submitRecovery(sessionId, playerName, event.getDialogResponseView());
            return;
        }

        if (PaperDialogActionKeys.PRE_JOIN_LOGIN_SUBMIT.equals(identifier)) {
            loginProcessor().processLogin(sessionId, connection, playerName, event.getDialogResponseView());
            return;
        }

        if (PaperDialogActionKeys.PRE_JOIN_REGISTER_SUBMIT.equals(identifier)) {
            registerProcessor().storePendingRegistration(connection, sessionId, playerName,
                event.getDialogResponseView());
            return;
        }

        if (PaperDialogActionKeys.PRE_JOIN_REGISTER_CANCEL.equals(identifier)) {
            String kickMessage = commonService.getProperty(RegistrationSettings.PRE_JOIN_REGISTER_CANCEL_KICKS)
                ? messages.retrieveSingle(playerName, MessageKey.DIALOG_REGISTER_CANCELED)
                : null;
            PaperDialogSessionSupport.completeResponse(pendingRegisterResponses, sessionId, kickMessage);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerConnectionClose(PlayerConnectionCloseEvent event) {
        // The event only carries a name and profile id, both shared by every connection using that
        // name, so retire by connection instead: the ones that are gone, whichever player they were
        for (PlayerConfigurationConnection connection : connectionSessions.keySet()) {
            if (!connection.isConnected()) {
                retireSession(connection);
            }
        }
        if (Bukkit.getPlayerExact(event.getPlayerName()) == null) {
            proxySessionManager.removeLoginRequest(event.getPlayerName());
        }
    }

    private PreJoinLoginProcessor loginProcessor() {
        return new PreJoinLoginProcessor(messages, loginCaptchaManager, tempbanManager, dataSource,
            passwordSecurity, bukkitService, preJoinDialogService, dialogWindowService, commonService,
            pendingLoginResponses);
    }

    private PreJoinRegisterProcessor registerProcessor() {
        return new PreJoinRegisterProcessor(commonService, messages, dataSource, validationService,
            registrationCaptchaManager, preJoinDialogService, dialogWindowService, pendingRegisterResponses);
    }

    private void retireSession(PlayerConfigurationConnection connection) {
        Long sessionId = connectionSessions.remove(connection);
        if (sessionId != null) {
            PaperDialogSessionSupport.releaseResponse(pendingLoginResponses.remove(sessionId));
            PaperDialogSessionSupport.releaseResponse(pendingRegisterResponses.remove(sessionId));
            preJoinDialogService.retireSession(sessionId);
        }
    }

    private void handleBlockingLoginDialog(PlayerConfigurationConnection connection, long sessionId, String playerName) {
        CompletableFuture<String> loginResponse = new CompletableFuture<>();
        PaperDialogSessionSupport.closeDialogOnCompletion(loginResponse, connection);
        int timeoutSeconds = commonService.getProperty(RestrictionSettings.LOGIN_TIMEOUT);
        if (timeoutSeconds > 0) {
            loginResponse.completeOnTimeout(
                messages.retrieveSingle(playerName, MessageKey.LOGIN_TIMEOUT_ERROR), timeoutSeconds, TimeUnit.SECONDS);
        }
        String normalizedName = playerName.toLowerCase(Locale.ROOT);
        pendingLoginResponses.put(sessionId, loginResponse);
        preJoinDialogService.registerPreJoinFuture(sessionId, loginResponse);

        // Close the race with a proxy auto-login (perform.login) that arrived during the configuration
        // phase between the shouldSkipDialogs() check and now: if a proxy session has been queued,
        // force-login instead of showing the dialog.
        if (proxySessionManager.shouldResumeSession(normalizedName)) {
            ProxySessionManager.ProxyLoginRequest req = proxySessionManager.getLoginRequest(normalizedName);
            if (req != null && (req.verifiedPremiumUuid() == null
                || PreJoinDialogGate.isProxyPremiumRequestValid(normalizedName, req, dataSource, pendingPremiumCache))) {
                preJoinDialogService.approvePreJoinForceLogin(normalizedName);
            }
        }

        if (!loginResponse.isDone()) {
            if (PreJoinDialogEventBridge.shouldShowDialog(PreJoinDialogEvent.DialogType.LOGIN, playerName,
                connection, bukkitService)) {
                connection.getAudience().showDialog(
                    PaperDialogHelper.createPreJoinLoginDialog(dialogWindowService.createPreJoinLoginDialog(playerName)));
            } else {
                // Skipping the dialog must not authenticate the player: the normal post-join flow applies.
                loginResponse.complete(null);
            }
        }
        String kickMessage = loginResponse.join();
        pendingLoginResponses.remove(sessionId);
        preJoinDialogService.unregisterPreJoinFuture(sessionId);

        if (kickMessage != null) {
            connection.disconnect(LegacyComponentSerializer.legacySection().deserialize(kickMessage));
        }
    }

    private void handleBlockingRegisterDialog(PlayerConfigurationConnection connection, long sessionId,
                                              String playerName, Dialog dialog) {
        CompletableFuture<String> registerResponse = new CompletableFuture<>();
        PaperDialogSessionSupport.closeDialogOnCompletion(registerResponse, connection);
        int timeoutSeconds = commonService.getProperty(RestrictionSettings.REGISTER_TIMEOUT);
        if (timeoutSeconds > 0) {
            registerResponse.completeOnTimeout(
                messages.retrieveSingle(playerName, MessageKey.LOGIN_TIMEOUT_ERROR), timeoutSeconds, TimeUnit.SECONDS);
        }
        pendingRegisterResponses.put(sessionId, registerResponse);

        if (PreJoinDialogEventBridge.shouldShowDialog(PreJoinDialogEvent.DialogType.REGISTER, playerName,
            connection, bukkitService)) {
            connection.getAudience().showDialog(dialog);
        } else {
            // Skipping the dialog must not authenticate the player: the normal post-join flow applies.
            registerResponse.complete(null);
        }
        String kickMessage = registerResponse.join();
        pendingRegisterResponses.remove(sessionId);

        if (kickMessage != null) {
            connection.disconnect(LegacyComponentSerializer.legacySection().deserialize(kickMessage));
        }
    }

    private boolean shouldSkipPreJoinDialogForPremium(PlayerAuth auth, String playerName, UUID playerId) {
        if (!commonService.getProperty(PremiumSettings.ENABLE_PREMIUM) || !auth.isPremium()) {
            return false;
        }
        if (playerId != null && playerId.version() == 4) {
            // UUID v4: Mojang UUID already in the profile (online-mode or proxy forwarded it).
            return playerId.equals(auth.getPremiumUuid());
        }
        // UUID v3 (offline): check if PacketEvents has already completed verification.
        UUID verifiedUuid = premiumLoginVerifier.getVerifiedUuid(playerName);
        if (verifiedUuid != null) {
            return verifiedUuid.equals(auth.getPremiumUuid());
        }
        // UUID is still offline at the configure phase but the player is enrolled as premium.
        // Proxy UUID forwarding may not have been applied yet — skip the blocking pre-join dialog
        // and let AsynchronousJoin.canBypassWithPremium() do the definitive check once the player
        // has fully joined and player.getUniqueId() reflects the proxy-forwarded Mojang UUID.
        return true;
    }
}
