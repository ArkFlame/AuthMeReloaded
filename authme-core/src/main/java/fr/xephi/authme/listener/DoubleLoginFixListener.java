package fr.xephi.authme.listener;

import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.service.CommonService;
import fr.xephi.authme.settings.properties.SecuritySettings;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import javax.inject.Inject;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Prevents "ghost players" by kicking a player whose name is already online.
 * The behavior is opt-in via {@link SecuritySettings#ANTI_GHOST_PLAYERS}.
 */
public class DoubleLoginFixListener implements Listener {

    @Inject
    private CommonService commonService;

    @EventHandler
    public void onDuplicateNameJoin(PlayerJoinEvent event) {
        if (!commonService.getProperty(SecuritySettings.ANTI_GHOST_PLAYERS)) {
            return;
        }

        Collection<? extends Player> onlinePlayers = Bukkit.getServer().getOnlinePlayers();
        Set<String> seenNames = new HashSet<>();
        for (Player player : onlinePlayers) {
            if (!seenNames.add(player.getName().toLowerCase(Locale.ROOT))) {
                player.kickPlayer(commonService.retrieveSingleMessage(player, MessageKey.DOUBLE_LOGIN_FIX));
                break;
            }
        }
    }
}
