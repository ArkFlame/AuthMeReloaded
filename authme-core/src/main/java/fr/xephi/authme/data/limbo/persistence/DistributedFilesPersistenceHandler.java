package fr.xephi.authme.data.limbo.persistence;

import com.google.common.io.Files;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import fr.xephi.authme.ConsoleLogger;
import fr.xephi.authme.data.limbo.LimboPlayer;
import fr.xephi.authme.initialization.DataFolder;
import fr.xephi.authme.output.ConsoleLoggerFactory;
import fr.xephi.authme.service.BukkitService;
import fr.xephi.authme.settings.Settings;
import fr.xephi.authme.settings.properties.LimboSettings;
import fr.xephi.authme.util.FileUtils;
import org.bukkit.entity.Player;

import javax.inject.Inject;
import java.io.File;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Persistence handler for LimboPlayer objects by distributing the objects to store
 * in various segments (buckets) based on the start of the player's UUID.
 */
class DistributedFilesPersistenceHandler implements LimboPersistenceHandler {

    private static final Type LIMBO_MAP_TYPE = new TypeToken<Map<String, LimboPlayer>>(){}.getType();

    private final ConsoleLogger logger = ConsoleLoggerFactory.get(DistributedFilesPersistenceHandler.class);
    private final File cacheFolder;
    private final Gson gson;
    private final SegmentNameBuilder segmentNameBuilder;

    @Inject
    DistributedFilesPersistenceHandler(@DataFolder File dataFolder, BukkitService bukkitService, Settings settings) {
        cacheFolder = new File(dataFolder, "playerdata");
        FileUtils.createDirectory(cacheFolder);

        gson = LimboPersistenceHandler.createGson(bukkitService);

        segmentNameBuilder = new SegmentNameBuilder(settings.getProperty(LimboSettings.DISTRIBUTION_SIZE));

        convertOldDataToCurrentSegmentScheme();
        deleteEmptyFiles();
    }

    @Override
    public synchronized LimboPlayer getLimboPlayer(Player player) {
        String uuid = player.getUniqueId().toString();
        File file = getPlayerSegmentFile(uuid);
        Map<String, LimboPlayer> entries = readLimboPlayers(file);
        return entries == null ? null : entries.get(uuid);
    }

    @Override
    public synchronized void saveLimboPlayer(Player player, LimboPlayer limbo) {
        String uuid = player.getUniqueId().toString();
        File file = getPlayerSegmentFile(uuid);

        Map<String, LimboPlayer> entries = null;
        if (file.exists()) {
            entries = readLimboPlayers(file);
        } else {
            FileUtils.create(file);
        }
        /* intentionally separate if */
        if (entries == null) {
            entries = new HashMap<>();
        }

        entries.put(uuid, limbo);
        saveEntries(entries, file);
    }

    @Override
    public synchronized void removeLimboPlayer(Player player) {
        String uuid = player.getUniqueId().toString();
        File file = getPlayerSegmentFile(uuid);
        if (file.exists()) {
            Map<String, LimboPlayer> entries = readLimboPlayers(file);
            if (entries != null && entries.remove(uuid) != null) {
                saveEntries(entries, file);
            }
        }
    }

    @Override
    public LimboPersistenceType getType() {
        return LimboPersistenceType.DISTRIBUTED_FILES;
    }

    private boolean saveEntries(Map<String, LimboPlayer> entries, File file) {
        // Write with the same charset that readLimboPlayers uses; FileWriter would silently use the
        // platform default and corrupt non-ASCII group names on systems where that is not UTF-8.
        // Write a temporary file and move it into place: a crash mid-write must not truncate the segment
        File temp = new File(file.getPath() + ".tmp");
        try {
            Files.asCharSink(temp, StandardCharsets.UTF_8).write(gson.toJson(entries));
            java.nio.file.Files.move(temp.toPath(), file.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (Exception e) {
            logger.logException("Could not write to '" + file + "':", e);
            FileUtils.delete(temp);
            return false;
        }
    }

    private Map<String, LimboPlayer> readLimboPlayers(File file) {
        if (!file.exists()) {
            return null;
        }

        try {
            return gson.fromJson(Files.asCharSource(file, StandardCharsets.UTF_8).read(), LIMBO_MAP_TYPE);
        } catch (Exception e) {
            logger.logException("Failed reading '" + file + "':", e);
        }
        return null;
    }

    private File getPlayerSegmentFile(String uuid) {
        return new File(cacheFolder, segmentNameBuilder.createSegmentName(uuid) + "-limbo.json");
    }

    /**
     * Loads segment files in the cache folder that don't correspond to the current segmenting scheme
     * and migrates the data into files of the current segments. This allows a player to change the
     * segment size without any loss of data.
     */
    private void convertOldDataToCurrentSegmentScheme() {
        String currentPrefix = segmentNameBuilder.getPrefix();
        File[] files = listFiles(cacheFolder);
        Map<String, LimboPlayer> allLimboPlayers = new HashMap<>();
        List<File> migratedFiles = new ArrayList<>();

        for (File file : files) {
            if (isLimboJsonFile(file) && !file.getName().startsWith(currentPrefix)) {
                Map<String, LimboPlayer> data = readLimboPlayers(file);
                if (data != null) {
                    allLimboPlayers.putAll(data);
                    migratedFiles.add(file);
                }
            }
        }

        if (!allLimboPlayers.isEmpty()) {
            // Keep the old files when any new segment could not be written: they hold the only copy
            if (saveToNewSegments(allLimboPlayers)) {
                migratedFiles.forEach(FileUtils::delete);
            }
        }
    }

    /**
     * Saves the LimboPlayer data read from old segmenting schemes into the current segmenting scheme.
     *
     * @param limbosFromOldSegments the limbo players to store into the current segment files
     */
    private boolean saveToNewSegments(Map<String, LimboPlayer> limbosFromOldSegments) {
        Map<String, Map<String, LimboPlayer>> limboBySegment = new HashMap<>();
        for (Map.Entry<String, LimboPlayer> entry : limbosFromOldSegments.entrySet()) {
            String segmentId = segmentNameBuilder.createSegmentName(entry.getKey());
            limboBySegment.computeIfAbsent(segmentId, s -> new HashMap<>())
                .put(entry.getKey(), entry.getValue());
        }

        boolean allSaved = true;
        logger.info("Saving " + limbosFromOldSegments.size() + " LimboPlayers from old segments into "
            + limboBySegment.size() + " current segments");
        for (Map.Entry<String, Map<String, LimboPlayer>> entry : limboBySegment.entrySet()) {
            File file = new File(cacheFolder, entry.getKey() + "-limbo.json");
            Map<String, LimboPlayer> limbosToSave = readLimboPlayers(file);
            if (limbosToSave == null) {
                limbosToSave = new HashMap<>();
            }
            limbosToSave.putAll(entry.getValue());
            allSaved &= saveEntries(limbosToSave, file);
        }
        return allSaved;
    }

    /**
     * Deletes segment files that are empty.
     */
    private void deleteEmptyFiles() {
        File[] files = listFiles(cacheFolder);

        int deletedFiles = 0;
        for (File file : files) {
            // typically the size is 2 because there's an empty JSON map: {}
            if (isLimboJsonFile(file) && file.length() < 3) {
                FileUtils.delete(file);
                deletedFiles++;
            }
        }
        logger.debug("Limbo: Deleted {0} empty segment files", deletedFiles);
    }

    /**
     * @param file the file to check
     * @return true if it is a segment file storing Limbo data, false otherwise
     */
    private static boolean isLimboJsonFile(File file) {
        String name = file.getName();
        return name.startsWith("seg") && name.endsWith("-limbo.json");
    }

    private File[] listFiles(File folder) {
        File[] files = folder.listFiles();
        if (files == null) {
            logger.warning("Could not get files of '" + folder + "'");
            return new File[0];
        }
        return files;
    }
}
