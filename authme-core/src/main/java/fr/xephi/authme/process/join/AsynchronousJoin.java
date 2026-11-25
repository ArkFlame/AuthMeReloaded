package fr.xephi.authme.process.join;

import fr.xephi.authme.ConsoleLogger;
import fr.xephi.authme.data.ProxySessionManager;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.data.auth.PlayerCache;
import fr.xephi.authme.data.limbo.LimboService;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.events.PlayerPreAuthEvent;
import fr.xephi.authme.events.ProtectInventoryEvent;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.output.ConsoleLoggerFactory;
import fr.xephi.authme.permission.PlayerStatePermission;
import fr.xephi.authme.platform.DialogAdapter;
import fr.xephi.authme.process.AsynchronousProcess;
import fr.xephi.authme.process.login.AsynchronousLogin;
import fr.xephi.authme.process.register.RegisterSecondaryArgument;
import fr.xephi.authme.process.register.RegistrationType;
import fr.xephi.authme.process.register.AsyncRegister;
import fr.xephi.authme.process.register.executors.ApiPasswordRegisterParams;
import fr.xephi.authme.process.register.executors.EmailRegisterParams;
import fr.xephi.authme.process.register.executors.PasswordRegisterParams;
import fr.xephi.authme.process.register.executors.RegistrationMethod;
import fr.xephi.authme.service.BukkitService;
import fr.xephi.authme.service.CommonService;
import fr.xephi.authme.service.DialogStateService;
import fr.xephi.authme.service.DialogWindowService;
import fr.xephi.authme.service.FloodgateService;
import fr.xephi.authme.service.PendingPremiumCache;
import fr.xephi.authme.service.PreJoinDialogService;
import fr.xephi.authme.service.PremiumLoginVerifier;
import fr.xephi.authme.service.PremiumService;
import fr.xephi.authme.service.PluginHookService;
import fr.xephi.authme.service.ProxyLoginRequestValidator;
import fr.xephi.authme.service.SessionService;
import fr.xephi.authme.service.ValidationService;
import fr.xephi.authme.service.bungeecord.BungeeSender;
import fr.xephi.authme.service.bungeecord.MessageType;
import fr.xephi.authme.settings.WelcomeMessageConfiguration;
import fr.xephi.authme.mail.EmailService;
import fr.xephi.authme.service.PasswordRecoveryService;
import fr.xephi.authme.service.RecoveryCodeService;
import fr.xephi.authme.settings.commandconfig.CommandManager;
import fr.xephi.authme.settings.properties.HooksSettings;
import fr.xephi.authme.settings.properties.PremiumSettings;
import fr.xephi.authme.settings.properties.RegistrationSettings;
import fr.xephi.authme.settings.properties.RestrictionSettings;
import fr.xephi.authme.settings.properties.SecuritySettings;
import fr.xephi.authme.util.InternetProtocolUtils;
import fr.xephi.authme.util.PlayerUtils;
import fr.xephi.authme.util.RandomStringUtils;
import fr.xephi.authme.util.Utils;
import org.bukkit.GameMode;
import org.bukkit.Server;
import org.bukkit.entity.Player;

import javax.inject.Inject;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static fr.xephi.authme.service.BukkitService.TICKS_PER_SECOND;
import static fr.xephi.authme.settings.properties.RestrictionSettings.PROTECT_INVENTORY_BEFORE_LOGIN;

/**
 * Asynchronous process for when a player joins.
 */
public class AsynchronousJoin implements AsynchronousProcess {

    /** Upper bound for waiting on {@link PlayerPreAuthEvent} listeners before continuing the regular join. */
    private long preAuthEventTimeoutMillis = 10_000L;

    /** Overrides the pre-auth event wait bound (for tests). */
    void setPreAuthEventTimeoutMillis(long millis) {
        this.preAuthEventTimeoutMillis = millis;
    }

    private final ConsoleLogger logger = ConsoleLoggerFactory.get(AsynchronousJoin.class);

    @Inject
    private Server server;

    @Inject
    private DataSource database;

    @Inject
    private CommonService service;

    @Inject
    private LimboService limboService;

    @Inject
    private PluginHookService pluginHookService;

    @Inject
    private BukkitService bukkitService;

    @Inject
    private AsynchronousLogin asynchronousLogin;

    @Inject
    private AsyncRegister asyncRegister;

    @Inject
    private CommandManager commandManager;

    @Inject
    private ValidationService validationService;

    @Inject
    private WelcomeMessageConfiguration welcomeMessageConfiguration;

    @Inject
    private SessionService sessionService;

    @Inject
    private PlayerCache playerCache;

    @Inject
    private BungeeSender bungeeSender;

    @Inject
    private ProxySessionManager proxySessionManager;

    @Inject
    private DialogAdapter dialogAdapter;

    @Inject
    private DialogWindowService dialogWindowService;

    @Inject
    private DialogStateService dialogStateService;

    @Inject
    private PreJoinDialogService preJoinDialogService;

    @Inject
    private PremiumLoginVerifier premiumLoginVerifier;

    @Inject
    private PendingPremiumCache pendingPremiumCache;

    @Inject
    private PremiumService premiumService;

    @Inject
    private FloodgateService floodgateService;

    @Inject
    private ProxyLoginRequestValidator proxyLoginRequestValidator;

    @Inject
    private EmailService emailService;

    @Inject
    private PasswordRecoveryService passwordRecoveryService;

    @Inject
    private RecoveryCodeService recoveryCodeService;

    AsynchronousJoin() {
    }

    /**
     * Processes the given player that has just joined.
     *
     * @param player the player to process
     */
    public void processJoin(Player player) {
        String name = player.getName().toLowerCase(Locale.ROOT);
        String ip = PlayerUtils.getPlayerIp(player);
        PreJoinDialogService.PendingDialogState dialogState = preJoinDialogService.consumeSession(name);
        String pendingLoginPassword = dialogState.loginPassword();
        String pendingRecoveryEmail = dialogState.recoveryEmail();
        PreJoinDialogService.PendingRegistration pendingRegistration = dialogState.registration();
        boolean shouldSkipPostJoinDialog = dialogState.skipPostJoinDialog();
        boolean pendingForceLogin = dialogState.forceLogin();
        String pendingKick = dialogState.kickMessage();
        // pendingKick is applied below, after proxy/premium/session checks, which take priority:
        // a Velocity perform.login arriving just after the player cancelled the pre-join dialog
        // must win over the dialog cancel kick.

        if (!validationService.fulfillsNameRestrictions(player)) {
            handlePlayerWithUnmetNameRestriction(player, ip);
            return;
        }

        if (service.getProperty(RestrictionSettings.UNRESTRICTED_NAMES).contains(name)) {
            return;
        }

        if (service.getProperty(RestrictionSettings.FORCE_SURVIVAL_MODE)
            && player.getGameMode() != GameMode.SURVIVAL
            && !service.hasPermission(player, PlayerStatePermission.BYPASS_FORCE_SURVIVAL)) {
            bukkitService.scheduleSyncTaskFromOptionallyAsyncTask(player, () -> player.setGameMode(GameMode.SURVIVAL));
        }

        if (service.getProperty(HooksSettings.DISABLE_SOCIAL_SPY)) {
            pluginHookService.setEssentialsSocialSpyStatus(player, false);
        }

        if (!validatePlayerCountForIp(player, ip)) {
            return;
        }

        // Single account lookup for the entire join. The same PlayerAuth is reused for the premium
        // check and for a listener-requested forced login, so no second query is needed later on.
        PlayerAuth auth = database.getAuth(name);
        boolean isAuthAvailable = auth != null;

        // Earliest possible hook for integrations: before session lookups, premium bypass, limbo and prompts.
        // The event is dispatched on the player's thread and its decision is applied before we continue.
        if (handlePreAuthEvent(player, isAuthAvailable, auth)) {
            return;
        }

        if (isAuthAvailable) {
            // Protect inventory
            if (service.getProperty(PROTECT_INVENTORY_BEFORE_LOGIN)) {
                ProtectInventoryEvent ev = bukkitService.createAndCallEvent(
                    isAsync -> new ProtectInventoryEvent(player, isAsync));
                if (ev.isCancelled()) {
                    bukkitService.scheduleSyncTaskFromOptionallyAsyncTask(player, player::updateInventory);
                    logger.fine("ProtectInventoryEvent has been cancelled for " + player.getName() + "...");
                }
            }

            // Bedrock players verified by Floodgate (Xbox account) — same idea as premium below
            if (floodgateService.canAutoLogin(player.getUniqueId())) {
                logger.info("The Bedrock player " + player.getName() + " has been automatically logged in (Floodgate).");
                bukkitService.runTaskOptionallyAsync(() -> asynchronousLogin.forceLogin(player));
                return;
            }

            // Premium players are always verified by Mojang — skip the session mechanism entirely,
            // which would send a misleading SESSION_RECONNECTION message and run session commands.
            if (canBypassWithPremium(player, auth)) {
                // Premium bypass: player has a verified Mojang UUID matching the connecting player's UUID
                if (bungeeSender.isEnabled()) {
                    // Proxy handles routing; do not attempt backend-side BungeeCord redirect
                    bukkitService.runTaskOptionallyAsync(() -> asynchronousLogin.forceLoginFromProxy(player));
                } else {
                    bukkitService.runTaskOptionallyAsync(() -> asynchronousLogin.forceLogin(player));
                }
                return;
            } else {
                ProxySessionManager.ProxyLoginRequest proxyLoginRequest = proxySessionManager.consumeLoginRequest(name);
                if (proxyLoginRequest != null) {
                    if (proxyLoginRequestValidator.validate(player, proxyLoginRequest.verifiedPremiumUuid())) {
                        if (playerCache.isAuthenticated(name)) {
                            return;
                        }
                        service.send(player, MessageKey.SESSION_RECONNECTION);
                        // Run commands
                        bukkitService.scheduleSyncTaskFromOptionallyAsyncTask(player,
                            () -> commandManager.runCommandsOnSessionLogin(player));
                        // Use forceLoginFromProxy (quiet=true, no BungeeCord redirect) so that if
                        // BungeeReceiver.performLogin() concurrently already completed the login, this
                        // call is a no-op rather than sending an "already logged in" error.
                        bukkitService.runTaskOptionallyAsync(() -> asynchronousLogin.forceLoginFromProxy(player));
                        logger.info("The user " + player.getName() + " has been automatically logged in, "
                            + "as present in autologin queue.");
                        return;
                    }
                    // Validation failed (e.g. premium UUID mismatch after /freemium).
                    // Fall through to session check / limbo flow below to avoid leaving the
                    // player stuck: not authenticated, no dialog, no movement allowed.
                }
            }
            if (sessionService.canResumeSession(player)) {
                service.send(player, MessageKey.SESSION_RECONNECTION);
                // Run commands
                bukkitService.scheduleSyncTaskFromOptionallyAsyncTask(player,
                    () -> commandManager.runCommandsOnSessionLogin(player));
                bukkitService.runTaskOptionallyAsync(() -> asynchronousLogin.forceLogin(player));
                return;
            }
        } else if (premiumService.tryAutoRegister(player)) {
            return;
        } else if (pendingRegistration == null && floodgateService.canAutoRegister(player.getUniqueId())) {
            logger.info("The Bedrock player " + player.getName() + " has been automatically registered (Floodgate).");
            asyncRegister.register(RegistrationMethod.API_REGISTRATION,
                ApiPasswordRegisterParams.of(player, generateFloodgatePassword(), true));
            return;
        } else if (!service.getProperty(RegistrationSettings.FORCE) && pendingRegistration == null) {
            bukkitService.scheduleSyncTaskFromOptionallyAsyncTask(player, () -> {
                limboService.restorePendingOperator(player);
                welcomeMessageConfiguration.sendWelcomeMessage(player);
            });

            // Skip if registration is optional

            if (bungeeSender.isEnabled()) {
                // As described at https://www.spigotmc.org/wiki/bukkit-bungee-plugin-messaging-channel/
                // "Keep in mind that you can't send plugin messages directly after a player joins."
                bukkitService.scheduleSyncDelayedTask(player, () ->
                    bungeeSender.sendAuthMeBungeecordMessage(player, MessageType.LOGIN), 5L);
            }
            return;
        }

        // Guard: a proxy-initiated forceLoginFromProxy() may have already authenticated the player
        // (if the perform.login message arrived and was processed before this async task completes).
        // Scheduling a limbo in that case would freeze the player permanently.
        if (playerCache.isAuthenticated(name)) {
            return;
        }

        // Apply the pre-join dialog cancel kick here, after proxy/premium/session checks above have
        // had a chance to take priority and return early. If perform.login arrived in time, the
        // player was already auto-logged in above and pendingKick is silently discarded.
        if (pendingKick != null) {
            bukkitService.scheduleSyncTaskFromOptionallyAsyncTask(player, () -> player.kickPlayer(pendingKick));
            return;
        }

        processJoinSync(player, isAuthAvailable, pendingLoginPassword, pendingRecoveryEmail, pendingRegistration, shouldSkipPostJoinDialog, pendingForceLogin);
    }

    /**
     * Fires {@link PlayerPreAuthEvent} on the player's owning thread and applies what listeners asked for.
     * The asynchronous join pipeline waits for the listeners to finish, so their decision is final.
     *
     * @param player the joining player
     * @param registered whether the player has an account
     * @param auth the account loaded for this join, null for an unregistered player
     * @return true if the join has been fully handled by a listener (forced login or skipped authentication)
     */
    private boolean handlePreAuthEvent(Player player, boolean registered, PlayerAuth auth) {
        final PlayerPreAuthEvent.Source source = bungeeSender.isEnabled()
            ? PlayerPreAuthEvent.Source.PROXY : PlayerPreAuthEvent.Source.JOIN;
        final CompletableFuture<PlayerPreAuthEvent> result = new CompletableFuture<>();

        // createAndCallEvent() would use the configurable async flag and run on the join thread.
        // The entity scheduler runs on the player's region thread on Folia and on the main thread on
        // Bukkit/Paper; the event is explicitly synchronous so listeners may safely touch the player.
        bukkitService.scheduleSyncTaskFromOptionallyAsyncTask(player, () -> {
            PlayerPreAuthEvent event = new PlayerPreAuthEvent(player, registered, source, false);
            try {
                bukkitService.callEvent(event);
            } catch (RuntimeException e) {
                // Bukkit logs and swallows listener exceptions; do the same so a faulty listener
                // cannot stall or abort the join.
                logger.fine("PlayerPreAuthEvent listener failed for " + player.getName() + ": " + e);
            } finally {
                result.complete(event);
            }
        });

        PlayerPreAuthEvent event = awaitPreAuthResult(result, player);
        if (event == null) {
            logger.fine("PlayerPreAuthEvent could not be dispatched for " + player.getName()
                + "; continuing with the regular join");
            return false;
        }
        if (event.isForceLoginRequested()) {
            logger.fine("PlayerPreAuthEvent: forced login requested for " + player.getName());
            bukkitService.runTaskOptionallyAsync(() -> performForcedLogin(player, auth));
            return true;
        }
        if (event.isCancelled()) {
            logger.fine("PlayerPreAuthEvent cancelled for " + player.getName() + ", skipping authentication");
            return true;
        }
        return false;
    }

    private PlayerPreAuthEvent awaitPreAuthResult(CompletableFuture<PlayerPreAuthEvent> result, Player player) {
        try {
            // Safety net only: the event normally completes within a tick. If the scheduler drops the task
            // (e.g. the player disconnected), fall back to the regular join instead of blocking forever.
            return result.get(preAuthEventTimeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            logger.fine("Timed out waiting for PlayerPreAuthEvent for " + player.getName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.fine("Interrupted while waiting for PlayerPreAuthEvent for " + player.getName());
        } catch (ExecutionException e) {
            logger.fine("Failed to dispatch PlayerPreAuthEvent for " + player.getName() + ": " + e.getCause());
        }
        return null;
    }

    /**
     * Applies a forced login requested by a {@link PlayerPreAuthEvent} listener. Like
     * {@code AsynchronousLogin.forceLogin}, this skips the password and TOTP check; unlike it, the
     * account is the one already loaded for this join, so no second database lookup is performed.
     * Online and authenticated guards keep the operation idempotent and safe if the player quit.
     *
     * @param player the joining player
     * @param auth the account loaded for this join, null for an unregistered player
     */
    private void performForcedLogin(Player player, PlayerAuth auth) {
        if (auth == null || !player.isOnline()) {
            logger.fine("PlayerPreAuthEvent: forced login skipped for offline or unregistered player "
                + player.getName());
            return;
        }
        if (playerCache.isAuthenticated(player.getName().toLowerCase(Locale.ROOT))) {
            logger.fine("PlayerPreAuthEvent: " + player.getName()
                + " is already authenticated, not logging in twice");
            return;
        }
        asynchronousLogin.performLogin(player, auth);
    }

    /**
     * Generates a random password for a Bedrock player registered automatically through Floodgate.
     * The player never types it; it only has to pass the configured password restrictions.
     */
    private String generateFloodgatePassword() {
        int length = Math.max(service.getProperty(SecuritySettings.MIN_PASSWORD_LENGTH),
            Math.min(24, service.getProperty(SecuritySettings.MAX_PASSWORD_LENGTH)));
        return RandomStringUtils.generate(length);
    }

    private void handlePlayerWithUnmetNameRestriction(Player player, String ip) {
        bukkitService.scheduleSyncTaskFromOptionallyAsyncTask(player,
            () -> player.kickPlayer(service.retrieveSingleMessage(player, MessageKey.NOT_OWNER_ERROR)));
        if (service.getProperty(RestrictionSettings.BAN_UNKNOWN_IP)) {
            bukkitService.runOnGlobalRegion(() -> server.banIP(ip));
        }
    }

    /**
     * Performs various operations in sync mode for an unauthenticated player (such as blindness effect and
     * limbo player creation).
     *
     * @param player the player to process
     * @param isAuthAvailable true if the player is registered, false otherwise
     */
    private void processJoinSync(Player player, boolean isAuthAvailable, String pendingLoginPassword,
                                 String pendingRecoveryEmail,
                                 PreJoinDialogService.PendingRegistration pendingRegistration,
                                 boolean shouldSkipPostJoinDialog, boolean pendingForceLogin) {
        int registrationTimeout = service.getProperty(
            isAuthAvailable ? RestrictionSettings.LOGIN_TIMEOUT : RestrictionSettings.REGISTER_TIMEOUT
        ) * TICKS_PER_SECOND;

        bukkitService.scheduleSyncTaskFromOptionallyAsyncTask(player, () -> {
            // Guard: proxy login may have completed between when this task was scheduled and now
            if (!player.isOnline() || playerCache.isAuthenticated(player.getName())) {
                return;
            }
            limboService.createLimboPlayer(player, isAuthAvailable);

            player.setNoDamageTicks(registrationTimeout);
            if (pluginHookService.isEssentialsAvailable() && service.getProperty(HooksSettings.USE_ESSENTIALS_MOTD)) {
                player.performCommand("motd");
            }
            if (service.getProperty(RegistrationSettings.APPLY_BLIND_EFFECT)) {
                // Allow infinite blindness effect
                int blindTimeOut = (registrationTimeout <= 0) ? 99999 : registrationTimeout;
                player.addPotionEffect(bukkitService.createBlindnessEffect(blindTimeOut));
            }
            commandManager.runCommandsOnJoin(player);
            if (pendingRegistration != null) {
                bukkitService.runTaskOptionallyAsync(() -> processPendingRegistration(player, pendingRegistration));
                return;
            }
            if (pendingLoginPassword != null) {
                bukkitService.runTaskOptionallyAsync(() -> asynchronousLogin.login(player, pendingLoginPassword));
                return;
            }
            if (pendingRecoveryEmail != null) {
                bukkitService.runTaskOptionallyAsync(() -> processPendingEmailRecovery(player, pendingRecoveryEmail));
                return;
            }
            if (pendingForceLogin) {
                bukkitService.runTaskOptionallyAsync(() -> asynchronousLogin.forceLogin(player));
                return;
            }
            if (!shouldSkipPostJoinDialog
                && !playerCache.isAuthenticated(player.getName())
                && service.getProperty(RegistrationSettings.USE_DIALOG_UI)
                && dialogAdapter.isDialogSupported()) {
                bukkitService.runTaskLater(player, () -> showPostJoinDialogIfNecessary(player, isAuthAvailable), 1L);
            }
        });
    }

    private void showPostJoinDialogIfNecessary(Player player, boolean isAuthAvailable) {
        if (!player.isOnline()
            || playerCache.isAuthenticated(player.getName())
            || !service.getProperty(RegistrationSettings.USE_DIALOG_UI)
            || !dialogAdapter.isDialogSupported()) {
            return;
        }

        if (isAuthAvailable) {
            dialogAdapter.showLoginDialog(player, dialogWindowService.createLoginDialog(player));
        } else {
            RegistrationType registrationType = service.getProperty(RegistrationSettings.REGISTRATION_TYPE);
            RegisterSecondaryArgument secondArg = service.getProperty(RegistrationSettings.REGISTER_SECOND_ARGUMENT);
            dialogAdapter.showRegisterDialog(player,
                registrationType,
                secondArg,
                dialogWindowService.createRegisterDialog(player, registrationType, secondArg));
        }
        dialogStateService.markDialogOpen(player);
    }

    private void processPendingEmailRecovery(Player player, String submittedEmail) {
        String playerName = player.getName();
        if (!emailService.hasAllInformation()) {
            kickFromPreJoinRecovery(player, MessageKey.INCOMPLETE_EMAIL_SETTINGS);
            return;
        }
        if (!validationService.validateEmail(submittedEmail)) {
            kickFromPreJoinRecovery(player, MessageKey.INVALID_EMAIL);
            return;
        }
        ch.jalu.datasourcecolumns.data.DataSourceValue<String> emailResult =
            database.getEmail(playerName.toLowerCase(Locale.ROOT));
        if (!emailResult.rowExists()) {
            kickFromPreJoinRecovery(player, MessageKey.USAGE_REGISTER);
            return;
        }
        String registeredEmail = emailResult.getValue();
        if (Utils.isEmailEmpty(registeredEmail) || !registeredEmail.equalsIgnoreCase(submittedEmail)) {
            kickFromPreJoinRecovery(player, MessageKey.INVALID_EMAIL);
            return;
        }
        if (recoveryCodeService.isRecoveryCodeNeeded()) {
            passwordRecoveryService.createAndSendRecoveryCode(player, registeredEmail);
        } else {
            passwordRecoveryService.generateAndSendNewPassword(player, registeredEmail);
        }
        // Player stays in limbo to use the recovery code or new password
    }

    private void kickFromPreJoinRecovery(Player player, MessageKey messageKey) {
        String kickMessage = service.retrieveSingleMessage(player, messageKey);
        bukkitService.scheduleSyncTaskFromOptionallyAsyncTask(player, () -> player.kickPlayer(kickMessage));
    }

    private void processPendingRegistration(Player player, PreJoinDialogService.PendingRegistration pendingRegistration) {
        if (pendingRegistration.isEmailRegistration()) {
            asyncRegister.register(RegistrationMethod.EMAIL_REGISTRATION,
                EmailRegisterParams.of(player, pendingRegistration.primaryValue()));
        } else {
            asyncRegister.register(RegistrationMethod.PASSWORD_REGISTRATION,
                PasswordRegisterParams.of(player, pendingRegistration.primaryValue(), pendingRegistration.secondaryValue()));
        }
    }

    /**
     * Checks whether the maximum number of accounts has been exceeded for the given IP address (according to
     * settings and permissions). If this is the case, the player is kicked.
     *
     * @param player the player to verify
     * @param ip     the ip address of the player
     *
     * @return true if the verification is OK (no infraction), false if player has been kicked
     */
    private boolean validatePlayerCountForIp(Player player, String ip) {
        if (service.getProperty(RestrictionSettings.MAX_JOIN_PER_IP) > 0
            && !service.hasPermission(player, PlayerStatePermission.ALLOW_MULTIPLE_ACCOUNTS)
            && !InternetProtocolUtils.isLoopbackAddress(ip)
            && countOnlinePlayersByIp(ip) > service.getProperty(RestrictionSettings.MAX_JOIN_PER_IP)) {

            bukkitService.scheduleSyncTaskFromOptionallyAsyncTask(player,
                () -> player.kickPlayer(service.retrieveSingleMessage(player, MessageKey.SAME_IP_ONLINE)));
            return false;
        }
        return true;
    }

    private boolean canBypassWithPremium(Player player, PlayerAuth auth) {
        if (!service.getProperty(PremiumSettings.ENABLE_PREMIUM) || auth == null) {
            return false;
        }
        String name = player.getName();

        if (!auth.isPremium()) {
            UUID playerId = player.getUniqueId();
            if (playerId.version() == 4) {
                // Proxy path: atomic gate prevents double-finalization with concurrent validate().
                UUID pendingUuid = pendingPremiumCache.removePending(name);
                if (pendingUuid == null) {
                    return false;
                }
                if (playerId.equals(pendingUuid)) {
                    premiumService.finalizePendingPremium(player, pendingUuid);
                    return true;
                }
                bungeeSender.sendPremiumUnset(name);
                service.send(player, MessageKey.PREMIUM_PENDING_FAIL);
                return false;
            } else if (bungeeSender.isEnabled()) {
                // Behind a proxy we intentionally keep the backend UUID on the offline v3 value, so
                // player.getUniqueId() is not proof of identity. If the signed perform.login is already
                // queued and carries the proxy-verified Mojang UUID, finalize the pending enrollment
                // in-join (mirrors the v4 path); otherwise defer to the perform.login message.
                ProxySessionManager.ProxyLoginRequest queued = proxySessionManager.getLoginRequest(name);
                UUID verified = queued == null ? null : queued.verifiedPremiumUuid();
                if (verified == null) {
                    logger.debug("canBypassWithPremium[v3/proxy]: " + name
                        + " has a pending premium enrollment but no queued verified UUID yet; deferring to perform.login");
                    return false;
                }
                // removePending is the atomic one-shot gate shared with ProxyLoginRequestValidator:
                // if a concurrent perform.login already consumed it, stay silent and let that path win.
                UUID pendingUuid = pendingPremiumCache.removePending(name);
                if (pendingUuid == null) {
                    logger.debug("canBypassWithPremium[v3/proxy]: pending entry for " + name
                        + " already consumed (concurrent perform.login); deferring");
                    return false;
                }
                if (verified.equals(pendingUuid)) {
                    logger.info("canBypassWithPremium[v3/proxy]: finalizing pending premium for " + name
                        + " from queued perform.login (verified=" + verified + ")");
                    premiumService.finalizePendingPremium(player, verified);
                    return true;
                }
                logger.warning("canBypassWithPremium[v3/proxy]: verified UUID " + verified
                    + " does not match pending UUID " + pendingUuid + " for " + name + "; failing enrollment");
                bungeeSender.sendPremiumUnset(name);
                service.send(player, MessageKey.PREMIUM_PENDING_FAIL);
                return false;
            } else {
                // No proxy: require cryptographic session verification via PacketEvents.
                UUID pendingUuid = pendingPremiumCache.getPendingUuid(name);
                if (pendingUuid == null) {
                    return false;
                }
                UUID verified = premiumLoginVerifier.consumeVerifiedUuid(name);
                UUID confirmedUuid = (verified != null && verified.equals(pendingUuid)) ? verified : null;
                pendingPremiumCache.removePending(name);
                if (confirmedUuid != null) {
                    premiumService.finalizePendingPremium(player, confirmedUuid);
                    return true;
                }
                bungeeSender.sendPremiumUnset(name);
                service.send(player, MessageKey.PREMIUM_PENDING_FAIL);
                return false;
            }
        }

        UUID playerId = player.getUniqueId();
        if (playerId.version() == 4) {
            // UUID v4 = Mojang online UUID (online-mode server or proxy forwarding): compare directly.
            // Security relies on the backend port being firewalled to only accept proxy connections.
            return playerId.equals(auth.getPremiumUuid());
        } else if (bungeeSender.isEnabled()) {
            // Behind a proxy, existing premium auto-login is validated through the signed
            // perform.login message rather than the backend player's offline UUID.
            return false;
        }
        // UUID v3 = Bukkit offline UUID: require cryptographic session verification via PacketEvents.
        UUID verifiedUuid = premiumLoginVerifier.consumeVerifiedUuid(name);
        return verifiedUuid != null && verifiedUuid.equals(auth.getPremiumUuid());
    }

    private int countOnlinePlayersByIp(String ip) {
        int count = 0;
        for (Player player : bukkitService.getOnlinePlayers()) {
            if (ip.equalsIgnoreCase(PlayerUtils.getPlayerIp(player))) {
                ++count;
            }
        }
        return count;
    }
}
