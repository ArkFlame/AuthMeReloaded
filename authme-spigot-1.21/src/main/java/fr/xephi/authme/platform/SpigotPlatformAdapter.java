package fr.xephi.authme.platform;

import fr.xephi.authme.listener.PlayerSignOpenListener;
import fr.xephi.authme.process.register.RegisterSecondaryArgument;
import fr.xephi.authme.process.register.RegistrationType;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import java.util.Collections;
import java.util.List;

/**
 * Platform adapter implementation for Spigot 1.20.x and 1.21.x.
 * Dialog UI is supported on Spigot 1.21.6+ (BungeeCord dialog API).
 * On older server versions in this range the dialog feature is gracefully disabled.
 */
public class SpigotPlatformAdapter extends AbstractSpigotPlatformAdapter {

    private static final SpigotCompatibility COMPATIBILITY = new SpigotCompatibility(
        "This AuthMe Spigot 1.21 build requires the Spigot 1.20+ API.",
        "org.spigotmc.event.player.PlayerSpawnLocationEvent",
        "org.bukkit.event.player.PlayerSignOpenEvent");

    @Override
    public String getPlatformName() {
        return "spigot-1.21";
    }

    @Override
    public String getCompatibilityError() {
        return COMPATIBILITY.getError();
    }

    @Override
    public List<Class<? extends Listener>> getListeners() {
        return EventRegistrationAdapter.combineListeners(
            super.getListeners(),
            Collections.singletonList(PlayerSignOpenListener.class));
    }

    /**
     * Spigot 1.20.6+ exposes {@link Player#getRespawnLocation()}, which preserves the server's actual respawn target
     * instead of limiting us to the legacy bed-only API.
     */
    @Override
    public Location getPlayerRespawnLocation(Player player) {
        return player.getRespawnLocation();
    }

    @Override
    public boolean isDialogSupported() {
        return DialogApiAvailability.isAvailable();
    }

    @Override
    public void showLoginDialog(Player player, DialogWindowSpec dialog) {
        SpigotDialogHelper.showLoginDialog(player, dialog);
    }

    @Override
    public void showTotpDialog(Player player, DialogWindowSpec dialog) {
        SpigotDialogHelper.showTotpDialog(player, dialog);
    }

    @Override
    public void showRegisterDialog(Player player, RegistrationType type,
                                   RegisterSecondaryArgument secondArg, DialogWindowSpec dialog) {
        SpigotDialogHelper.showRegisterDialog(player, type, secondArg, dialog);
    }

    @Override
    public void showRecoveryCodeDialog(Player player, DialogWindowSpec dialog) {
        SpigotDialogHelper.showRecoveryCodeDialog(player, dialog);
    }

    @Override
    public void showNewPasswordDialog(Player player, DialogWindowSpec dialog) {
        SpigotDialogHelper.showNewPasswordDialog(player, dialog);
    }

    @Override
    public void closeDialog(Player player) {
        if (DialogApiAvailability.isAvailable()) {
            SpigotDialogHelper.closeDialog(player);
        }
    }
}
