package fr.xephi.authme.task;

import fr.xephi.authme.data.auth.PlayerCache;
import org.bukkit.entity.Player;

/**
 * Kicks a player if he hasn't logged in (scheduled to run after a configured delay).
 */
public class TimeoutTask implements Runnable {

    private final Player player;
    private final String message;
    private final PlayerCache playerCache;

    public TimeoutTask(Player player, String message, PlayerCache playerCache) {
        this.message = message;
        this.player = player;
        this.playerCache = playerCache;
    }

    @Override
    public void run() {
        if (!playerCache.isAuthenticated(player.getName())) {
            player.kickPlayer(message);
        }
    }
}
