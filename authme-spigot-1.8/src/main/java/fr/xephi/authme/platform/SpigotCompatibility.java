package fr.xephi.authme.platform;

import fr.xephi.authme.util.Utils;

/**
 * Immutable description of the server classes a Spigot build requires.
 *
 * <p>Keeping the requirement (message plus class names) and the check in one small
 * object lets each version module declare its compatibility in a single place.</p>
 */
final class SpigotCompatibility {

    private final String errorMessage;
    private final String[] requiredClasses;

    SpigotCompatibility(String errorMessage, String... requiredClasses) {
        this.errorMessage = errorMessage;
        this.requiredClasses = requiredClasses.clone();
    }

    /**
     * Returns the error message when any required class is missing, or null when compatible.
     *
     * @return the compatibility error, or null if the server is compatible
     */
    String getError() {
        for (String requiredClass : requiredClasses) {
            if (!Utils.isClassLoaded(requiredClass)) {
                return errorMessage;
            }
        }
        return null;
    }
}
