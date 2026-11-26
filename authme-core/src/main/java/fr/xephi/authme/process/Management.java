package fr.xephi.authme.process;

import fr.xephi.authme.ConsoleLogger;
import fr.xephi.authme.data.ProxySessionManager;
import fr.xephi.authme.output.ConsoleLoggerFactory;
import fr.xephi.authme.process.changepassword.AsyncChangePassword;
import fr.xephi.authme.process.email.AsyncAddEmail;
import fr.xephi.authme.process.email.AsyncChangeEmail;
import fr.xephi.authme.process.email.AsyncConfirmEmail;
import fr.xephi.authme.process.join.AsynchronousJoin;
import fr.xephi.authme.process.login.AsynchronousLogin;
import fr.xephi.authme.process.logout.AsynchronousLogout;
import fr.xephi.authme.process.quit.AsynchronousQuit;
import fr.xephi.authme.process.register.AsyncRegister;
import fr.xephi.authme.process.register.executors.RegistrationMethod;
import fr.xephi.authme.process.register.executors.RegistrationParameters;
import fr.xephi.authme.process.unregister.AsynchronousUnregister;
import fr.xephi.authme.service.BukkitService;
import fr.xephi.authme.service.PreJoinDialogService;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import javax.inject.Inject;
import java.util.Locale;

/**
 * Performs auth actions, e.g. when a player joins, registers or wants to change his password.
 */
public class Management {

    private final ConsoleLogger logger = ConsoleLoggerFactory.get(Management.class);

    @Inject
    private BukkitService bukkitService;

    @Inject
    private ProxySessionManager proxySessionManager;

    @Inject
    private PreJoinDialogService preJoinDialogService;

    // Processes
    @Inject
    private AsyncAddEmail asyncAddEmail;
    @Inject
    private AsyncChangeEmail asyncChangeEmail;
    @Inject
    private AsynchronousLogout asynchronousLogout;
    @Inject
    private AsynchronousQuit asynchronousQuit;
    @Inject
    private AsynchronousJoin asynchronousJoin;
    @Inject
    private AsyncRegister asyncRegister;
    @Inject
    private AsynchronousLogin asynchronousLogin;
    @Inject
    private AsynchronousUnregister asynchronousUnregister;
    @Inject
    private AsyncChangePassword asyncChangePassword;
    @Inject
    private AsyncConfirmEmail asyncConfirmEmail;

    Management() {
    }

    public void performLogin(Player player, String password) {
        bukkitService.runTaskOptionallyAsync(() -> asynchronousLogin.login(player, password));
    }

    public void forceLogin(Player player) {
        bukkitService.runTaskOptionallyAsync(() -> asynchronousLogin.forceLogin(player));
    }

    public void forceLoginFromProxy(Player player) {
        bukkitService.runTaskOptionallyAsync(() -> asynchronousLogin.forceLoginFromProxy(player));
    }

    public void forceLoginFromProxy(String playerName) {
        String normalizedName = playerName.toLowerCase(Locale.ROOT);
        proxySessionManager.processProxySessionMessage(playerName);
        Player player = bukkitService.getPlayerExact(playerName);
        if (player != null && player.isOnline()) {
            forceLoginFromProxy(player);
        } else {
            preJoinDialogService.approvePreJoinForceLogin(normalizedName);
        }
    }

    public void performLogout(Player player) {
        // Capture location on the main thread before handing off to the async task. The lookup must never
        // abort the submission: if it fails the logout is still queued, only the saved quit location is lost.
        Location quitLocation = captureQuitLocation(player);
        bukkitService.runTaskOptionallyAsync(() -> asynchronousLogout.logout(player, quitLocation));
    }

    private Location captureQuitLocation(Player player) {
        try {
            return player.getLocation();
        } catch (RuntimeException e) {
            logger.warning("Could not capture the logout location of '" + player.getName() + "': " + e.getMessage());
            return null;
        }
    }

    public <P extends RegistrationParameters> void performRegister(RegistrationMethod<P> variant, P parameters) {
        bukkitService.runTaskOptionallyAsync(() -> asyncRegister.register(variant, parameters));
    }

    public void performUnregister(Player player, String password) {
        bukkitService.runTaskOptionallyAsync(() -> asynchronousUnregister.unregister(player, password));
    }

    public void performUnregisterByAdmin(CommandSender initiator, String name, Player player) {
        bukkitService.runTaskOptionallyAsync(() -> asynchronousUnregister.adminUnregister(initiator, name, player));
    }

    public void performJoin(Player player) {
        bukkitService.runTaskOptionallyAsync(() -> asynchronousJoin.processJoin(player));
    }

    public void performQuit(Player player) {
        bukkitService.runTaskOptionallyAsync(() -> asynchronousQuit.processQuit(player));
    }

    public void performAddEmail(Player player, String newEmail) {
        bukkitService.runTaskOptionallyAsync(() -> asyncAddEmail.addEmail(player, newEmail));
    }

    public void performChangeEmail(Player player, String oldEmail, String newEmail) {
        bukkitService.runTaskOptionallyAsync(() -> asyncChangeEmail.changeEmail(player, oldEmail, newEmail));
    }

    public void performConfirmEmail(Player player, String code) {
        bukkitService.runTaskOptionallyAsync(() -> asyncConfirmEmail.confirmEmail(player, code));
    }

    public void performPasswordChange(Player player, String oldPassword, String newPassword) {
        bukkitService.runTaskOptionallyAsync(
            () -> asyncChangePassword.changePassword(player, oldPassword, newPassword));
    }

    public void performPasswordChangeAsAdmin(CommandSender sender, String playerName, String newPassword) {
        bukkitService.runTaskOptionallyAsync(
            () -> asyncChangePassword.changePasswordAsAdmin(sender, playerName, newPassword));
    }
}
