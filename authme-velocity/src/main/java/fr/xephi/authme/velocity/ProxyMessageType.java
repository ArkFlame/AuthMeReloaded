package fr.xephi.authme.velocity;

/**
 * Message identifiers exchanged with backend AuthMe servers over the {@code authme:main} channel.
 * Keeping the wire-protocol ids in one place avoids duplicating string literals between the
 * parsing and message-building code in {@link VelocityProxyBridge}.
 */
enum ProxyMessageType {

    LOGIN("login"),
    LOGOUT("logout"),
    PERFORM_LOGIN("perform.login"),
    PERFORM_LOGIN_ACK("perform.login.ack"),
    PROXY_STARTED("proxy.started"),
    PREMIUM_SET("premium.set"),
    PREMIUM_UNSET("premium.unset"),
    PREMIUM_LIST("premium.list"),
    PREMIUM_LIST_CHUNK("premium.list.chunk"),
    PREMIUM_PENDING_SET("premium.pending.set");

    private final String id;

    ProxyMessageType(String id) {
        this.id = id;
    }

    String id() {
        return id;
    }

    static ProxyMessageType fromId(String id) {
        for (ProxyMessageType type : values()) {
            if (type.id.equals(id)) {
                return type;
            }
        }
        return null;
    }
}
