package fr.xephi.authme.process.login;

import com.google.common.annotations.VisibleForTesting;
import fr.xephi.authme.ConsoleLogger;
import fr.xephi.authme.data.TempbanManager;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.data.auth.PlayerCache;
import fr.xephi.authme.data.captcha.LoginCaptchaManager;
import fr.xephi.authme.data.limbo.LimboMessageType;
import fr.xephi.authme.data.limbo.LimboPlayer;
import fr.xephi.authme.data.limbo.LimboPlayerState;
import fr.xephi.authme.data.limbo.LimboService;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.events.AuthMeAsyncPreLoginEvent;
import fr.xephi.authme.events.FailedLoginEvent;
import fr.xephi.authme.output.ConsoleLoggerFactory;
import fr.xephi.authme.mail.EmailService;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.permission.AdminPermission;
import fr.xephi.authme.permission.PlayerPermission;
import fr.xephi.authme.platform.DialogAdapter;
import fr.xephi.authme.process.AsynchronousProcess;
import fr.xephi.authme.process.ProcessSharedActions;
import fr.xephi.authme.process.SyncProcessManager;
import fr.xephi.authme.security.PasswordSecurity;
import fr.xephi.authme.service.BukkitService;
import fr.xephi.authme.service.CommonService;
import fr.xephi.authme.service.DialogStateService;
import fr.xephi.authme.service.DialogWindowService;
import fr.xephi.authme.service.SessionService;
import fr.xephi.authme.service.bungeecord.BungeeSender;
import fr.xephi.authme.settings.properties.DatabaseSettings;
import fr.xephi.authme.settings.properties.EmailSettings;
import fr.xephi.authme.settings.properties.HooksSettings;
import fr.xephi.authme.settings.properties.PluginSettings;
import fr.xephi.authme.settings.properties.RegistrationSettings;
import fr.xephi.authme.settings.properties.RestrictionSettings;
import fr.xephi.authme.util.PlayerUtils;
import fr.xephi.authme.util.Utils;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import javax.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Asynchronous task for a player login. */
public class AsynchronousLogin implements AsynchronousProcess {

    private final ConsoleLogger logger;

    /** Lowercase names of players whose login is currently being processed, to ignore duplicate requests. */
    private final Set<String> loginsInProgress = Collections.newSetFromMap(new ConcurrentHashMap<>());

    @Inject
    private DataSource dataSource;

    @Inject
    private CommonService service;

    @Inject
    private PlayerCache playerCache;

    @Inject
    private SyncProcessManager syncProcessManager;

    @Inject
    private BukkitService bukkitService;

    @Inject
    private PasswordSecurity passwordSecurity;

    @Inject
    private LoginCaptchaManager loginCaptchaManager;

    @Inject
    private TempbanManager tempbanManager;

    @Inject
    private LimboService limboService;

    @Inject
    private EmailService emailService;

    @Inject
    private SessionService sessionService;

    @Inject
    private BungeeSender bungeeSender;

    @Inject
    private DialogAdapter dialogAdapter;

    @Inject
    private DialogWindowService dialogWindowService;

    @Inject
    private DialogStateService dialogStateService;

    AsynchronousLogin() {
        logger = ConsoleLoggerFactory.get(AsynchronousLogin.class);
    }

    /** Processes a player's login request. */
    public void login(Player player, String password) {
        login(player, password, false);
    }

    /** Logs a player in without requiring a password. */
    public void forceLogin(Player player) {
        login(player, null, false);
    }

    /** Logs a player in without a password, initiated by the proxy: quiet and no BungeeCord redirect. */
    public void forceLoginFromProxy(Player player) {
        login(player, null, true);
    }

    /** Shared login implementation; a null password forces the login without password or TOTP checks. */
    private void login(Player player, String password, boolean quiet) {
        String name = player.getName();
        String lowerCaseName = name.toLowerCase(Locale.ROOT);
        // Ignore a duplicate request while the same player's login is still being processed
        if (!loginsInProgress.add(lowerCaseName)) {
            return;
        }
        try {
            PlayerAuth auth = getPlayerAuth(player, lowerCaseName, quiet);
            if (auth == null) {
                return;
            }
            // Resolve the IP once for the whole login call and pass it to every check that needs it
            String ip = PlayerUtils.getPlayerIp(player);
            if (!canLogin(player, ip, quiet)
                || (password != null && !checkPlayerInfo(player, name, lowerCaseName, ip, auth, password))) {
                return;
            }
            if (password != null && auth.getTotpKey() != null) {
                LimboPlayer limbo = limboService.getLimboPlayer(player);
                if (limbo == null) {
                    service.send(player, MessageKey.LOGIN_MESSAGE);
                    return;
                }
                limboService.resetMessageTask(player, LimboMessageType.TOTP_CODE);
                limbo.setState(LimboPlayerState.TOTP_REQUIRED);
                showTotpDialogIfEnabled(player, lowerCaseName);
                // TODO #1141: Check if we should check limbo state before processing password
            } else {
                doPerformLogin(player, name, ip, auth, quiet);
            }
        } finally {
            loginsInProgress.remove(lowerCaseName);
        }
    }

    /** Returns the player's auth object, or null if the player does not exist or may not log in. */
    private PlayerAuth getPlayerAuth(Player player, String lowerCaseName, boolean quiet) {
        if (playerCache.isAuthenticated(lowerCaseName)) {
            sendUnlessQuiet(player, MessageKey.ALREADY_LOGGED_IN_ERROR, quiet);
            return null;
        }

        PlayerAuth auth = dataSource.getAuth(lowerCaseName);
        if (auth == null) {
            sendUnlessQuiet(player, MessageKey.UNKNOWN_USER, quiet);
            // Recreate the message task to immediately send the message again as response
            limboService.resetMessageTask(player, LimboMessageType.REGISTER);
            return null;
        }

        if (!service.getProperty(DatabaseSettings.MYSQL_COL_GROUP).isEmpty()
            && auth.getGroupId() == service.getProperty(HooksSettings.NON_ACTIVATED_USERS_GROUP)) {
            sendUnlessQuiet(player, MessageKey.ACCOUNT_NOT_ACTIVATED, quiet);
            return null;
        }
        return auth;
    }

    private void sendUnlessQuiet(Player player, MessageKey key, boolean quiet) {
        if (!quiet) {
            service.send(player, key);
        }
    }

    /** Checks the per-IP limit and fires the pre-login event; true if the player may log in. */
    private boolean canLogin(Player player, String ip, boolean quiet) {
        if (hasReachedMaxLoggedInPlayersForIp(player, ip)) {
            sendUnlessQuiet(player, MessageKey.ALREADY_LOGGED_IN_ERROR, quiet);
            return false;
        }
        AuthMeAsyncPreLoginEvent event = new AuthMeAsyncPreLoginEvent(player,
            service.getProperty(PluginSettings.USE_ASYNC_TASKS));
        bukkitService.callEvent(event);
        return event.canLogin();
    }

    /** Checks captcha and password for a regular login; true if the password matches. */
    private boolean checkPlayerInfo(Player player, String name, String lowerCaseName, String ip, PlayerAuth auth,
                                    String password) {
        // If captcha is required send a message to the player and deny to log in
        if (loginCaptchaManager.isCaptchaRequired(lowerCaseName)) {
            service.send(player, MessageKey.USAGE_CAPTCHA, loginCaptchaManager.getCaptchaCodeOrGenerateNew(lowerCaseName));
            return false;
        }

        // Increase the counts here before knowing the result of the login.
        loginCaptchaManager.increaseLoginFailureCount(lowerCaseName);
        tempbanManager.increaseCount(ip, lowerCaseName);

        if (!passwordSecurity.comparePassword(password, auth.getPassword(), name)) {
            handleWrongPassword(player, name, auth, ip);
            return false;
        }
        return true;
    }

    /** Handles a login with wrong password. */
    private void handleWrongPassword(Player player, String name, PlayerAuth auth, String ip) {
        logger.fine(name + " used the wrong password");

        bukkitService.createAndCallEvent(isAsync -> new FailedLoginEvent(player, isAsync));
        if (tempbanManager.shouldTempban(ip)) {
            tempbanManager.tempbanPlayer(player);
        } else if (service.getProperty(RestrictionSettings.KICK_ON_WRONG_PASSWORD)) {
            ProcessSharedActions.kickPlayer(bukkitService, player, service.retrieveSingleMessage(player, MessageKey.WRONG_PASSWORD));
        } else {
            service.send(player, MessageKey.WRONG_PASSWORD);

            // If the authentication fails check if Captcha is required and send a message to the player
            if (loginCaptchaManager.isCaptchaRequired(name)) {
                limboService.muteMessageTask(player);
                service.send(player, MessageKey.USAGE_CAPTCHA, loginCaptchaManager.getCaptchaCodeOrGenerateNew(name));
            } else if (emailService.hasAllInformation() && !Utils.isEmailEmpty(auth.getEmail())) {
                service.send(player, MessageKey.FORGOT_PASSWORD_MESSAGE);
            }
        }
    }

    private void showTotpDialogIfEnabled(Player player, String lowerCaseName) {
        boolean dialogEnabled = service.getProperty(RegistrationSettings.USE_DIALOG_UI)
            || service.getProperty(RegistrationSettings.USE_PREJOIN_DIALOG_UI);
        if (dialogEnabled && dialogAdapter.isDialogSupported()) {
            bukkitService.runTaskLater(player, () -> {
                if (!player.isOnline() || playerCache.isAuthenticated(lowerCaseName)) {
                    return;
                }
                dialogAdapter.showTotpDialog(player, dialogWindowService.createTotpDialog(player));
                dialogStateService.markDialogOpen(player);
            }, 1L);
        }
    }

    /** Sets the player to the logged in state. */
    public void performLogin(Player player, PlayerAuth auth) {
        // Callers outside the login flow have no IP resolved yet; doPerformLogin resolves it lazily
        doPerformLogin(player, player.getName(), null, auth, false);
    }

    private void doPerformLogin(Player player, String name, String ip, PlayerAuth auth, boolean proxyInitiated) {
        // A concurrent login attempt may have authenticated the player already; never log in twice
        if (playerCache.isAuthenticated(name)) {
            logger.fine("Player '" + name + "' is already authenticated, aborted duplicate login");
            return;
        }
        if (!player.isOnline()) {
            logger.warning("Player '" + name + "' wasn't online during login process, aborted...");
            return;
        }
        boolean isFirstLogin = auth.getLastLogin() == null;
        // Update auth to reflect this new login
        if (ip == null) {
            ip = PlayerUtils.getPlayerIp(player);
        }
        auth.setRealName(name);
        auth.setLastLogin(System.currentTimeMillis());
        auth.setLastIp(ip);
        dataSource.updateSession(auth);

        // TODO: send an update when a messaging service will be implemented (SESSION)

        // Successful login, so reset the captcha & temp ban count
        loginCaptchaManager.resetLoginFailureCount(name);
        tempbanManager.resetCount(ip, name);

        service.send(player, MessageKey.LOGIN_SUCCESS);

        // Other auths
        List<String> auths = dataSource.getAllAuthsByIp(ip);
        displayOtherAccounts(auths, name);

        if (service.getProperty(EmailSettings.RECALL_PLAYERS) && Utils.isEmailEmpty(auth.getEmail())) {
            service.send(player, MessageKey.ADD_EMAIL_MESSAGE);
        }

        logger.fine(name + " logged in " + ip);

        // makes player loggedin
        playerCache.updatePlayer(auth);
        dataSource.setLogged(name);
        sessionService.grantSession(player);

        if (!player.isOnline()) {
            playerCache.removePlayer(name);
            dataSource.setUnlogged(name);
            sessionService.revokeSession(name);
            logger.warning("Player '" + name + "' quit during the login process, login reverted");
            return;
        }

        ProcessSharedActions.sendLoginMessageToProxy(bukkitService, bungeeSender, player);
        // Schedule the sync login task last so it runs after the current task
        if (proxyInitiated) {
            syncProcessManager.processSyncPlayerLoginFromProxy(player, isFirstLogin, auths);
        } else {
            syncProcessManager.processSyncPlayerLogin(player, isFirstLogin, auths);
        }
    }

    /** Sends info about the other accounts owned by the given player to the configured users. */
    private void displayOtherAccounts(List<String> auths, String playerName) {
        int accountCount = auths.size();
        if (!service.getProperty(RestrictionSettings.DISPLAY_OTHER_ACCOUNTS) || accountCount <= 1) {
            return;
        }

        // Build the final message in one pass: no intermediate list, joined String or concatenated copies
        StringBuilder builder = new StringBuilder().append(ChatColor.GRAY);
        boolean first = true;
        for (String currentName : auths) {
            if (!first) {
                builder.append(", ");
            }
            first = false;
            Player currentPlayer = bukkitService.getPlayerExact(currentName);
            if (currentPlayer != null && currentPlayer.isOnline()) {
                builder.append(ChatColor.GREEN).append(currentPlayer.getName()).append(ChatColor.GRAY);
            } else {
                builder.append(currentName);
            }
        }
        String message = builder.append('.').toString();

        logger.fine("The user " + playerName + " has " + accountCount + " accounts:");
        logger.fine(message);
        String countArg = Integer.toString(accountCount);
        for (Player onlinePlayer : bukkitService.getOnlinePlayers()) {
            String header;
            if (onlinePlayer.getName().equalsIgnoreCase(playerName)
                && service.hasPermission(onlinePlayer, PlayerPermission.SEE_OWN_ACCOUNTS)) {
                header = service.retrieveSingleMessage(onlinePlayer, MessageKey.ACCOUNTS_OWNED_SELF, countArg);
            } else if (service.hasPermission(onlinePlayer, AdminPermission.SEE_OTHER_ACCOUNTS)) {
                header = service.retrieveSingleMessage(onlinePlayer, MessageKey.ACCOUNTS_OWNED_OTHER,
                    playerName, countArg);
            } else {
                continue;
            }
            bukkitService.scheduleSyncTaskFromOptionallyAsyncTask(onlinePlayer, () -> {
                onlinePlayer.sendMessage(header);
                onlinePlayer.sendMessage(message);
            });
        }
    }

    /** True when the maximum number of logged in players per IP address has been reached. */
    @VisibleForTesting
    boolean hasReachedMaxLoggedInPlayersForIp(Player player, String ip) {
        int maxLoggedInPerIp = service.getProperty(RestrictionSettings.MAX_LOGIN_PER_IP);
        // Do not perform the check if player has multiple accounts permission or if IP is localhost
        if (maxLoggedInPerIp <= 0 || ProcessSharedActions.isExemptFromIpLimit(service, player, ip)) {
            return false;
        }

        // Count logged in players with same IP address
        String name = player.getName();
        int count = 0;
        for (Player onlinePlayer : bukkitService.getOnlinePlayers()) {
            String onlineName = onlinePlayer.getName();
            // The caller's IP was already resolved; do not look its socket address up a second time
            if ((onlinePlayer == player || ip.equalsIgnoreCase(PlayerUtils.getPlayerIp(onlinePlayer)))
                && !onlineName.equals(name)
                && dataSource.isLogged(onlineName.toLowerCase(Locale.ROOT))) {
                ++count;
            }
        }
        return count >= maxLoggedInPerIp;
    }
}
