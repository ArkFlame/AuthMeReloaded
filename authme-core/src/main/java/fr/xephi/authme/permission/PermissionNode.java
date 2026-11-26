package fr.xephi.authme.permission;

/**
 * Common interface for AuthMe permission nodes.
 */
public interface PermissionNode {

    /**
     * Return the node of the permission, e.g. "authme.player.unregister".
     *
     * @return The name of the permission node
     */
    String getNode();

    /**
     * Return the default permission for this node, e.g. "OP_ONLY".
     * Most AuthMe nodes fall back to {@link DefaultPermission#OP_ONLY}; nodes with a
     * different fallback override this method.
     *
     * @return The default level of permission
     */
    default DefaultPermission getDefaultPermission() {
        return DefaultPermission.OP_ONLY;
    }
}
