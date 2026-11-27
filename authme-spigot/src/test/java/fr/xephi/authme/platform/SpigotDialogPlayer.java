package fr.xephi.authme.platform;

import net.md_5.bungee.api.dialog.Dialog;
import org.bukkit.entity.Player;

/** Stand-in for a Spigot server's Player, which has dialog methods that Paper's Player API lacks. */
public interface SpigotDialogPlayer extends Player {

    void showDialog(Dialog dialog);

    void clearDialog();
}
