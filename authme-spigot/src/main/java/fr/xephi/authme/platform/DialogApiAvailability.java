package fr.xephi.authme.platform;

/**
 * Detects, once, whether the BungeeCord dialog API shipped with Spigot 1.21.6+ is present.
 *
 * <p>The check lives in its own class so that {@link SpigotDialogHelper}, which directly
 * references those classes, is never loaded on servers that do not have them. Only a class
 * name is probed via reflection, so this is safe on older servers in the 1.20/1.21 range.</p>
 */
final class DialogApiAvailability {

    private static final String DIALOG_API_CLASS = "net.md_5.bungee.api.dialog.MultiActionDialog";

    // Probing must never abort plugin startup: a class that exists but cannot be linked (e.g. an
    // incompatible bungee-chat version) throws a LinkageError, which is treated as unavailable.
    private static final boolean AVAILABLE =
        isPresent(DIALOG_API_CLASS, DialogApiAvailability.class.getClassLoader());

    private DialogApiAvailability() {
    }

    static boolean isAvailable() {
        return AVAILABLE;
    }

    /**
     * Probes a class without initializing it, treating both absence and link failure as unavailable.
     *
     * @param className the binary class name to check
     * @param classLoader the loader to resolve it with
     *
     * @return true if the class resolves, false otherwise
     */
    static boolean isPresent(String className, ClassLoader classLoader) {
        try {
            Class.forName(className, false, classLoader);
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }
}
