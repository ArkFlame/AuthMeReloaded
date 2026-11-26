package fr.xephi.authme.service.bungeecord;

import java.util.Optional;

public enum MessageType {
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

    /**
     * Plugin-messaging channel shared by the backend and the proxy side of the bridge.
     */
    public static final String CHANNEL = "authme:main";

    /**
     * Standard BungeeCord plugin-messaging channel used to send players between servers.
     */
    public static final String BUNGEE_CHANNEL = "BungeeCord";

    private final String id;

    MessageType(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    /**
     * Returns the MessageType with the given ID.
     *
     * @param id the message type id.
     *
     * @return the MessageType with the given id, empty if invalid.
     */
    public static Optional<MessageType> fromId(String id) {
        for (MessageType current : values()) {
            if (current.getId().equals(id)) {
                return Optional.of(current);
            }
        }
        return Optional.empty();
    }

}
