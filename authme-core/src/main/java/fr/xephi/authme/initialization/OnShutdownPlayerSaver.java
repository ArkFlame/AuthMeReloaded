package fr.xephi.authme.initialization;

import fr.xephi.authme.ConsoleLogger;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.data.auth.PlayerCache;
import fr.xephi.authme.data.limbo.LimboService;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.output.ConsoleLoggerFactory;
import fr.xephi.authme.service.BukkitService;
import fr.xephi.authme.service.ValidationService;
import fr.xephi.authme.settings.Settings;
import fr.xephi.authme.settings.SpawnLoader;
import fr.xephi.authme.settings.properties.RestrictionSettings;
import fr.xephi.authme.util.PlayerUtils;
import org.bukkit.entity.Player;

import javax.inject.Inject;
import java.util.Locale;

/**
 * Saves all players' data when the plugin shuts down.
 */
public class OnShutdownPlayerSaver {

    private final ConsoleLogger logger = ConsoleLoggerFactory.get(OnShutdownPlayerSaver.class);

    @Inject
    private BukkitService bukkitService;
    @Inject
    private Settings settings;
    @Inject
    private ValidationService validationService;
    @Inject
    private DataSource dataSource;
    @Inject
    private SpawnLoader spawnLoader;
    @Inject
    private PlayerCache playerCache;
    @Inject
    private LimboService limboService;

    /**
     * Saves the data of all online players.
     */
    public void saveAllPlayers() {
        for (Player player : bukkitService.getOnlinePlayers()) {
            try {
                savePlayer(player);
            } catch (RuntimeException e) {
                logger.logException("Could not save the data of '" + player.getName() + "' on shutdown:", e);
            }
        }
    }

    private void savePlayer(Player player) {
        String playerName = player.getName();
        String name = playerName.toLowerCase(Locale.ROOT);
        if (PlayerUtils.isNpc(player) || validationService.isUnrestricted(name)) {
            return;
        }
        try {
            if (limboService.hasLimboPlayer(player)) {
                limboService.restoreData(player);
            } else {
                limboService.restorePendingOperator(player);
                if (settings.getProperty(RestrictionSettings.SAVE_QUIT_LOCATION)) {
                    dataSource.updateQuitLoc(PlayerAuth.builder()
                        .name(name)
                        .realName(playerName)
                        .location(spawnLoader.getPlayerLocationOrSpawn(player)).build());
                    // TODO: send an update when a messaging service will be implemented (QUITLOC)
                }
            }
        } finally {
            playerCache.removePlayer(name);
        }
    }
}
