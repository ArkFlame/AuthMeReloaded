package fr.xephi.authme.data.limbo.persistence;

import com.google.common.io.Files;
import com.google.gson.Gson;
import fr.xephi.authme.ConsoleLogger;
import fr.xephi.authme.data.limbo.LimboPlayer;
import fr.xephi.authme.initialization.DataFolder;
import fr.xephi.authme.output.ConsoleLoggerFactory;
import fr.xephi.authme.service.BukkitService;
import fr.xephi.authme.util.FileUtils;
import org.bukkit.entity.Player;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Saves LimboPlayer objects as JSON into individual files.
 */
class IndividualFilesPersistenceHandler implements LimboPersistenceHandler {

    private final ConsoleLogger logger = ConsoleLoggerFactory.get(IndividualFilesPersistenceHandler.class);
    private final Gson gson;
    private final File cacheDir;

    @Inject
    IndividualFilesPersistenceHandler(@DataFolder File dataFolder, BukkitService bukkitService) {
        cacheDir = new File(dataFolder, "playerdata");
        if (!cacheDir.exists() && !cacheDir.mkdir()) {
            logger.warning("Failed to create playerdata directory '" + cacheDir + "'");
        }
        gson = LimboPersistenceHandler.createGson(bukkitService);
    }

    @Override
    public LimboPlayer getLimboPlayer(Player player) {
        File file = new File(getPlayerFolder(player), "data.json");
        if (!file.exists()) {
            return null;
        }
        try {
            String str = Files.asCharSource(file, StandardCharsets.UTF_8).read();
            return gson.fromJson(str, LimboPlayer.class);
        } catch (IOException e) {
            logger.logException("Could not read player data on disk for '" + player.getName() + "'", e);
            return null;
        }
    }

    @Override
    public void saveLimboPlayer(Player player, LimboPlayer limboPlayer) {
        try {
            File file = new File(getPlayerFolder(player), "data.json");
            Files.createParentDirs(file);
            Files.write(gson.toJson(limboPlayer), file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.logException("Failed to write " + player.getName() + " data:", e);
        }
    }

    /**
     * Removes the player's "playerdata/&lt;uuid&gt;/" folder from disk.
     * @param player player to remove
     */
    @Override
    public void removeLimboPlayer(Player player) {
        File file = getPlayerFolder(player);
        if (file.exists()) {
            FileUtils.purgeDirectory(file);
            FileUtils.delete(file);
        }
    }

    @Override
    public LimboPersistenceType getType() {
        return LimboPersistenceType.INDIVIDUAL_FILES;
    }

    private File getPlayerFolder(Player player) {
        return new File(cacheDir, player.getUniqueId().toString());
    }
}
