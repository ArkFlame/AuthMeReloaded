package fr.xephi.authme.service;

import fr.xephi.authme.util.StringUtils;

import javax.inject.Inject;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds join messages until they can be broadcast.
 */
public class JoinMessageService {

    private final BukkitService bukkitService;
    private final Map<String, String> joinMessages;

    @Inject
    JoinMessageService(BukkitService bukkitService) {
        this.bukkitService = bukkitService;
        joinMessages = new ConcurrentHashMap<>();
    }

    /**
     * Store a join message.
     *
     * @param playerName the player name
     * @param string     the join message
     */
    public void putMessage(String playerName, String string) {
        joinMessages.put(key(playerName), string);
    }

    /** Drops a stored join message of a player who left without logging in. */
    public void removeMessage(String playerName) {
        joinMessages.remove(key(playerName));
    }

    /**
     * Broadcast the join message of the specified player.
     *
     * @param playerName the player name
     */
    public void sendMessage(String playerName) {
        String joinMessage = joinMessages.remove(key(playerName));
        if (!StringUtils.isBlank(joinMessage)) {
            bukkitService.broadcastMessage(joinMessage);
        }
    }

    /** Join messages are stored lowercase, but callers pass either the real or the lowercase name. */
    private static String key(String playerName) {
        return playerName.toLowerCase(Locale.ROOT);
    }
}
