package fr.xephi.authme.platform;

/**
 * Detects, once, whether the BungeeCord dialog API shipped with Spigot 1.21.6+ is present.
 *
 * <p>The check lives in its own class so that {@link SpigotDialogHelper}, which directly
 * references those classes, is never loaded on servers that do not have them. Only a class
 * name is probed via reflection, so this is safe on older servers in the 1.20/1.21 range.</p>
 */
final class DialogApiAvailability {

    private static final boolean AVAILABLE = detect();

    private DialogApiAvailability() {
    }

    static boolean isAvailable() {
        return AVAILABLE;
    }

    private static boolean detect() {
        try {
            Class.forName("net.md_5.bungee.api.dialog.MultiActionDialog");
            return true;
        } catch (ClassNotFoundException ignored) {
            // BungeeCord dialog API absent (Spigot < 1.21.6)
            return false;
        }
    }
}
