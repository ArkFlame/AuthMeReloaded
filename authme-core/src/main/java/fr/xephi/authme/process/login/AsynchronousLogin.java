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
import fr.xephi.authme.output.LogLevel;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Asynchronous task for a player login.
 */
public class AsynchronousLogin implements AsynchronousProcess {
    
    private final ConsoleLogger logger = ConsoleLoggerFactory.get(AsynchronousLogin.class);

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
    }

    /**
     * Processes a player's login request.
     *
     * @param player the player to log in
     * @param password the password to log in with
     */
    public void login(Player player, String password) {
        String name = player.getName();
        String lowerCaseName = name.toLowerCase(Locale.ROOT);
        PlayerAuth auth = getPlayerAuth(player, lowerCaseName, false);
        if (auth == null) {
            return;
        }
        // Resolve the IP once for the whole login call and pass it to every check that needs it
        String ip = PlayerUtils.getPlayerIp(player);
        if (!canLogin(player, ip, false) || !checkPlayerInfo(player, name, lowerCaseName, ip, auth, password)) {
            return;
        }
        if (auth.getTotpKey() != null) {
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
            doPerformLogin(player, name, ip, auth, false);
        }
    }

    /**
     * Logs a player in without requiring a password.
     *
     * @param player the player to log in
     */
    public void forceLogin(Player player) {
        forceLogin(player, false);
    }

    /**
     * Logs a player in without requiring a password, initiated by the proxy.
     * Suppresses pre-condition messages and skips the BungeeCord server redirect
     * (the proxy manages routing).
     *
     * @param player the player to log in
     */
    public void forceLoginFromProxy(Player player) {
        forceLogin(player, true);
    }

    /**
     * Shared force-login implementation for locally and proxy initiated logins.
     *
     * @param player the player to log in
     * @param proxyInitiated true when the proxy initiated the login; suppresses
     *                       pre-condition messages and skips the BungeeCord redirect
     */
    private void forceLogin(Player player, boolean proxyInitiated) {
        String name = player.getName();
        String lowerCaseName = name.toLowerCase(Locale.ROOT);
        PlayerAuth auth = getPlayerAuth(player, lowerCaseName, proxyInitiated);
        if (auth == null) {
            return;
        }
        String ip = PlayerUtils.getPlayerIp(player);
        if (canLogin(player, ip, proxyInitiated)) {
            doPerformLogin(player, name, ip, auth, proxyInitiated);
        }
    }

    /**
     * Checks the precondition for authentication (like user known) and returns
     * the player's {@link PlayerAuth} object.
     *
     * @param player the player to check
     * @param lowerCaseName the lower-case name, resolved once for this login call
     * @param quiet don't send messages
     * @return the PlayerAuth object, or {@code null} if the player doesn't exist or may not log in
     *         (e.g. because he is already logged in)
     */
    private PlayerAuth getPlayerAuth(Player player, String lowerCaseName, boolean quiet) {
        if (playerCache.isAuthenticated(lowerCaseName)) {
            if (!quiet) {
                service.send(player, MessageKey.ALREADY_LOGGED_IN_ERROR);
            }
            return null;
        }

        PlayerAuth auth = dataSource.getAuth(lowerCaseName);
        if (auth == null) {
            if (!quiet) {
                service.send(player, MessageKey.UNKNOWN_USER);
            }
            // Recreate the message task to immediately send the message again as response
            limboService.resetMessageTask(player, LimboMessageType.REGISTER);
            return null;
        }

        if (!service.getProperty(DatabaseSettings.MYSQL_COL_GROUP).isEmpty()
            && auth.getGroupId() == service.getProperty(HooksSettings.NON_ACTIVATED_USERS_GROUP)) {
            if (!quiet) {
                service.send(player, MessageKey.ACCOUNT_NOT_ACTIVATED);
            }
            return null;
        }
        return auth;
    }

    /**
     * Checks the per-IP limit and fires the pre-login event, using the IP address already resolved
     * for this login call.
     *
     * @param player the player to check
     * @param ip the IP address of the player, resolved once for this login call
     * @param quiet don't send messages
     * @return true if the player may log in, false otherwise
     */
    private boolean canLogin(Player player, String ip, boolean quiet) {
        if (hasReachedMaxLoggedInPlayersForIp(player, ip)) {
            if (!quiet) {
                service.send(player, MessageKey.ALREADY_LOGGED_IN_ERROR);
            }
            return false;
        }
        boolean isAsync = service.getProperty(PluginSettings.USE_ASYNC_TASKS);
        AuthMeAsyncPreLoginEvent event = new AuthMeAsyncPreLoginEvent(player, isAsync);
        bukkitService.callEvent(event);
        return event.canLogin();
    }

    /**
     * Checks various conditions for regular player login (not used in force login).
     *
     * @param player the player requesting to log in
     * @param name the player's name, resolved once for this login call
     * @param lowerCaseName the lower-case name, resolved once for this login call
     * @param ip the IP address of the player, resolved once for this login call
     * @param auth the PlayerAuth object of the player
     * @param password the password supplied by the player
     * @return true if the password matches and all other conditions are met (e.g. no captcha required),
     *         false otherwise
     */
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

    /**
     * Handles a login with wrong password.
     *
     * @param player the player who attempted to log in
     * @param name the player's name, resolved once for this login call
     * @param auth the PlayerAuth object of the player
     * @param ip the ip address of the player
     */
    private void handleWrongPassword(Player player, String name, PlayerAuth auth, String ip) {
        logger.fine(name + " used the wrong password");

        bukkitService.createAndCallEvent(isAsync -> new FailedLoginEvent(player, isAsync));
        if (tempbanManager.shouldTempban(ip)) {
            tempbanManager.tempbanPlayer(player);
        } else if (service.getProperty(RestrictionSettings.KICK_ON_WRONG_PASSWORD)) {
            bukkitService.scheduleSyncTaskFromOptionallyAsyncTask(player,
                () -> player.kickPlayer(service.retrieveSingleMessage(player, MessageKey.WRONG_PASSWORD)));
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
        if (!dialogEnabled || !dialogAdapter.isDialogSupported()) {
            return;
        }

        bukkitService.runTaskLater(player, () -> {
            if (!player.isOnline() || playerCache.isAuthenticated(lowerCaseName)) {
                return;
            }
            dialogAdapter.showTotpDialog(player, dialogWindowService.createTotpDialog(player));
            dialogStateService.markDialogOpen(player);
        }, 1L);
    }

    /**
     * Sets the player to the logged in state.
     *
     * @param player the player to log in
     * @param auth the associated PlayerAuth object
     */
    public void performLogin(Player player, PlayerAuth auth) {
        // Callers outside the login flow have no IP resolved yet; doPerformLogin resolves it lazily
        doPerformLogin(player, player.getName(), null, auth, false);
    }

    private void doPerformLogin(Player player, String name, String ip, PlayerAuth auth, boolean proxyInitiated) {
        if (!player.isOnline()) {
            logger.warning("Player '" + name + "' wasn't online during login process, aborted...");
            return;
        }
        boolean isFirstLogin = (auth.getLastLogin() == null);
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

        String email = auth.getEmail();
        if (service.getProperty(EmailSettings.RECALL_PLAYERS) && Utils.isEmailEmpty(email)) {
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
        // As the scheduling executes the Task most likely after the current
        // task, we schedule it in the end
        // so that we can be sure, and have not to care if it might be
        // processed in other order.
        if (proxyInitiated) {
            syncProcessManager.processSyncPlayerLoginFromProxy(player, isFirstLogin, auths);
        } else {
            syncProcessManager.processSyncPlayerLogin(player, isFirstLogin, auths);
        }
    }

    /**
     * Sends info about the other accounts owned by the given player to the configured users.
     *
     * @param auths the names of the accounts also owned by the player
     * @param playerName the player's name, resolved once for this login call
     */
    private void displayOtherAccounts(List<String> auths, String playerName) {
        int accountCount = auths.size();
        if (!service.getProperty(RestrictionSettings.DISPLAY_OTHER_ACCOUNTS) || accountCount <= 1) {
            return;
        }

        String message = formatAccountNames(auths);
        if (logger.getLogLevel().includes(LogLevel.FINE)) {
            logger.fine("The user " + playerName + " has " + accountCount + " accounts:");
            logger.fine(message);
        }
        notifyOnlinePlayersAboutAccounts(playerName, accountCount, message);
    }

    private String formatAccountNames(List<String> auths) {
        List<String> formattedNames = new ArrayList<>(auths.size());
        for (String currentName : auths) {
            Player currentPlayer = bukkitService.getPlayerExact(currentName);
            if (currentPlayer != null && currentPlayer.isOnline()) {
                formattedNames.add(ChatColor.GREEN + currentPlayer.getName() + ChatColor.GRAY);
            } else {
                formattedNames.add(currentName);
            }
        }
        return ChatColor.GRAY + String.join(", ", formattedNames) + ".";
    }

    private void notifyOnlinePlayersAboutAccounts(String playerName, int accountCount, String message) {
        String accountCountText = Integer.toString(accountCount);
        String[] selfHeaderArgs = {accountCountText};
        String[] otherHeaderArgs = {playerName, accountCountText};
        for (Player onlinePlayer : bukkitService.getOnlinePlayers()) {
            MessageKey headerKey;
            String[] headerArgs;
            if (onlinePlayer.getName().equalsIgnoreCase(playerName)
                && service.hasPermission(onlinePlayer, PlayerPermission.SEE_OWN_ACCOUNTS)) {
                headerKey = MessageKey.ACCOUNTS_OWNED_SELF;
                headerArgs = selfHeaderArgs;
            } else if (service.hasPermission(onlinePlayer, AdminPermission.SEE_OTHER_ACCOUNTS)) {
                headerKey = MessageKey.ACCOUNTS_OWNED_OTHER;
                headerArgs = otherHeaderArgs;
            } else {
                continue;
            }
            String header = service.retrieveSingleMessage(onlinePlayer, headerKey, headerArgs);
            bukkitService.scheduleSyncTaskFromOptionallyAsyncTask(onlinePlayer, () -> {
                onlinePlayer.sendMessage(header);
                onlinePlayer.sendMessage(message);
            });
        }
    }

    /**
     * Checks whether the maximum threshold of logged in player per IP address has been reached
     * for the given player and IP address.
     *
     * @param player the player to process
     * @param ip the associated ip address
     * @return true if the threshold has been reached, false otherwise
     */
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
            if (ip.equalsIgnoreCase(PlayerUtils.getPlayerIp(onlinePlayer))
                && !onlineName.equals(name)
                && dataSource.isLogged(onlineName.toLowerCase(Locale.ROOT))) {
                ++count;
            }
        }
        return count >= maxLoggedInPerIp;
    }
}
