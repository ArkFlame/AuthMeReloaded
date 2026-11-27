package fr.xephi.authme.proxy;

/**
 * Message identifiers exchanged with backend AuthMe servers over the {@code authme:main} channel.
 * Shared by the Bungee and Velocity bridges so both sides use the same wire-protocol ids.
 */
public enum ProxyMessageType {

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

    private static final ProxyMessageType[] VALUES = values();

    private final String id;

    ProxyMessageType(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static ProxyMessageType fromId(String id) {
        for (ProxyMessageType type : VALUES) {
            if (type.id.equals(id)) {
                return type;
            }
        }
        return null;
    }
}
