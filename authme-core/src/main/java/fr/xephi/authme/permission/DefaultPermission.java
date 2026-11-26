package fr.xephi.authme.permission;

import org.bukkit.permissions.ServerOperator;

import java.util.function.Predicate;

/**
 * The default permission to fall back to if there is no support for permission nodes.
 */
public enum DefaultPermission {

    /** No one has permission. */
    NOT_ALLOWED(sender -> false),

    /** Only players with OP status have permission. */
    OP_ONLY(sender -> sender != null && sender.isOp()),

    /** Everyone is granted permission. */
    ALLOWED(sender -> true);

    private final Predicate<ServerOperator> predicate;

    DefaultPermission(Predicate<ServerOperator> predicate) {
        this.predicate = predicate;
    }

    /**
     * Evaluates whether permission is granted to the sender or not.
     *
     * @param sender the sender to process
     * @return true if the sender has permission, false otherwise
     */
    public boolean evaluate(ServerOperator sender) {
        return predicate.test(sender);
    }
}
