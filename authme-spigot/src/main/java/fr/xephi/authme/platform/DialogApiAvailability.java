package fr.xephi.authme.platform;

import fr.xephi.authme.util.Utils;

/**
 * Detects, once, whether the BungeeCord dialog API shipped with Spigot 1.21.6+ is present.
 *
 * <p>The check lives in its own class so that {@link SpigotDialogHelper}, which directly
 * references those classes, is never loaded on servers that do not have them. Only a class
 * name is probed via reflection, so this is safe on older servers in the 1.20/1.21 range.</p>
 */
final class DialogApiAvailability {

    // Same Class.forName probe as the core hook; no dialog API class is referenced directly here.
    private static final boolean AVAILABLE =
        Utils.isClassLoaded("net.md_5.bungee.api.dialog.MultiActionDialog");

    private DialogApiAvailability() {
    }

    static boolean isAvailable() {
        return AVAILABLE;
    }
}
