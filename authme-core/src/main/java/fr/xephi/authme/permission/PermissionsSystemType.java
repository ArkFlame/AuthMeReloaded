package fr.xephi.authme.permission;

/**
 * Enum representing the permissions systems AuthMe supports.
 */
public enum PermissionsSystemType {

    /**
     *  LuckPerms.
     */
    LUCK_PERMS("LuckPerms"),

    /**
     * Vault.
     */
    VAULT("Vault");

    /**
     * The name of the permissions system (both display name and plugin name).
     */
    private final String name;

    /**
     * Constructor for PermissionsSystemType.
     *
     * @param name Name of the permissions system.
     */
    PermissionsSystemType(String name) {
        this.name = name;
    }

    /**
     * Return the plugin name.
     *
     * @return Plugin name.
     */
    public String getPluginName() {
        return name;
    }

    /**
     * Cast the permissions system type to a string.
     *
     * @return The display name of the permissions system.
     */
    @Override
    public String toString() {
        return name;
    }

    /**
     * Check if a given plugin is a permissions system.
     *
     * @param name The name of the plugin to check.
     * @return If the plugin is a valid permissions system.
     */
    public static boolean isPermissionSystem(String name) {
        for (PermissionsSystemType type : values()) {
            if (type.name.equals(name)) {
                return true;
            }
        }
        return false;
    }
}
