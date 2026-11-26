package fr.xephi.authme.settings;

import fr.xephi.authme.ConsoleLogger;
import fr.xephi.authme.initialization.DataFolder;
import fr.xephi.authme.initialization.Reloadable;
import fr.xephi.authme.output.ConsoleLoggerFactory;
import fr.xephi.authme.platform.BukkitCompatibilityAdapter;
import fr.xephi.authme.platform.TeleportAdapter;
import fr.xephi.authme.service.PluginHookService;
import fr.xephi.authme.settings.properties.HooksSettings;
import fr.xephi.authme.settings.properties.RestrictionSettings;
import fr.xephi.authme.util.FileUtils;
import fr.xephi.authme.util.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.function.Function;

/**
 * Manager for spawn points. It loads spawn definitions from AuthMe and third-party plugins
 * and is responsible for returning the correct spawn point as per the settings.
 * <p>
 * The spawn priority setting defines from which sources and in which order the spawn point
 * should be taken from. In AuthMe, we can distinguish between the regular spawn and a "first spawn",
 * to which players will be teleported who have joined for the first time.
 */
public class SpawnLoader implements Reloadable {

    private final ConsoleLogger logger = ConsoleLoggerFactory.get(SpawnLoader.class);

    private final File authMeConfigurationFile;
    private final Settings settings;
    private final PluginHookService pluginHookService;
    private final TeleportAdapter teleportAdapter;
    private final BukkitCompatibilityAdapter compatibilityAdapter;
    private FileConfiguration authMeConfiguration;
    private String[] spawnPriority;
    private Location essentialsSpawn;
    private Location cmiSpawn;

    /**
     * Constructor.
     *
     * @param pluginFolder The AuthMe data folder
     * @param settings     The setting instance
     * @param pluginHookService  The plugin hooks instance
     * @param teleportAdapter The platform-specific teleport adapter
     */
    @Inject
    SpawnLoader(@DataFolder File pluginFolder, Settings settings, PluginHookService pluginHookService,
                TeleportAdapter teleportAdapter, BukkitCompatibilityAdapter compatibilityAdapter) {
        File spawnFile = new File(pluginFolder, "spawn.yml");
        FileUtils.copyFileFromResource(spawnFile, "spawn.yml");
        this.authMeConfigurationFile = spawnFile;
        this.settings = settings;
        this.pluginHookService = pluginHookService;
        this.teleportAdapter = teleportAdapter;
        this.compatibilityAdapter = compatibilityAdapter;
        reload();
    }

    /**
     * (Re)loads the spawn file and relevant settings.
     */
    @Override
    public void reload() {
        spawnPriority = settings.getProperty(RestrictionSettings.SPAWN_PRIORITY).split(",");
        authMeConfiguration = YamlConfiguration.loadConfiguration(authMeConfigurationFile);
        loadEssentialsSpawn();
    }

    /**
     * Return the AuthMe spawn location.
     *
     * @return The location of the regular AuthMe spawn point
     */
    public Location getSpawn() {
        return getLocationFromConfiguration(authMeConfiguration, "spawn");
    }

    /**
     * Set the AuthMe spawn point.
     *
     * @param location The location to use
     *
     * @return True upon success, false otherwise
     */
    public boolean setSpawn(Location location) {
        return setLocation("spawn", location);
    }

    /**
     * Return the AuthMe first spawn location.
     *
     * @return The location of the AuthMe spawn point for first timers
     */
    public Location getFirstSpawn() {
        return getLocationFromConfiguration(authMeConfiguration, "firstspawn");
    }

    /**
     * Set the AuthMe first spawn location.
     *
     * @param location The location to use
     *
     * @return True upon success, false otherwise
     */
    public boolean setFirstSpawn(Location location) {
        return setLocation("firstspawn", location);
    }

    /**
     * Load the spawn point defined in EssentialsSpawn.
     */
    public void loadEssentialsSpawn() {
        // EssentialsSpawn cannot run without Essentials, so it's fine to get the Essentials data folder
        File essentialsFolder = pluginHookService.getEssentialsDataFolder();
        if (essentialsFolder == null) {
            return;
        }
        essentialsSpawn = loadSpawnFromFile(essentialsFolder, "spawn.yml",
            config -> getLocationFromConfiguration(config, "spawns.default"), "Essentials spawn file");
    }

    /**
     * Unset the spawn point defined in EssentialsSpawn.
     */
    public void unloadEssentialsSpawn() {
        essentialsSpawn = null;
    }

    /**
     * Load the spawn point defined in CMI.
     */
    public void loadCmiSpawn() {
        File cmiFolder = pluginHookService.getCmiDataFolder();
        if (cmiFolder == null) {
            return;
        }
        cmiSpawn = loadSpawnFromFile(cmiFolder, "config.yml",
            config -> buildLocation(config, "Spawn.Main", "World", "X", "Y", "Z", "Yaw", "Pitch"),
            "CMI config file");
    }

    /**
     * Reads a spawn location from a file in a third-party plugin's data folder.
     * Returns null (and logs) when the file is absent; a null data folder is handled by the caller
     * so that an existing spawn location is left untouched in that case.
     *
     * @param dataFolder  the plugin's data folder
     * @param fileName    the name of the configuration file to read
     * @param parser      extracts the location from the configuration
     * @param description human-readable description used in the "not found" log message
     * @return the parsed spawn location, or null if the file is missing
     */
    private Location loadSpawnFromFile(File dataFolder, String fileName,
                                       Function<FileConfiguration, Location> parser, String description) {
        File configFile = new File(dataFolder, fileName);
        if (configFile.exists()) {
            return parser.apply(YamlConfiguration.loadConfiguration(configFile));
        }
        logger.info(description + " not found: '" + configFile.getAbsolutePath() + "'");
        return null;
    }

    /**
     * Unset the spawn point defined in CMI.
     */
    public void unloadCmiSpawn() {
        cmiSpawn = null;
    }

    /**
     * Return the spawn location for the given player. The source of the spawn location varies
     * depending on the spawn priority setting.
     *
     * @param player The player to retrieve the spawn point for
     *
     * @return The spawn location, or the default spawn location upon failure
     *
     * @see RestrictionSettings#SPAWN_PRIORITY
     */
    public Location getSpawnLocation(Player player) {
        if (player == null) {
            return null;
        }
        return getSpawnLocation(player.getWorld());
    }

    /**
     * Return the spawn location for the given world. The source of the spawn location varies
     * depending on the spawn priority setting.
     *
     * @param world The world to retrieve the spawn point for
     *
     * @return The spawn location, or the default spawn location upon failure
     */
    public Location getSpawnLocation(World world) {
        if (world == null) {
            return null;
        }

        Location spawnLoc = null;
        for (String priority : spawnPriority) {
            String source = priority.toLowerCase(Locale.ROOT).trim();
            switch (source) {
                case "default":
                    if (world.getSpawnLocation() != null) {
                        if (!isValidSpawnPoint(world.getSpawnLocation())) {
                            World alternativeWorld = findValidSpawnWorld();
                            if (alternativeWorld != null) {
                                world = alternativeWorld;
                            }
                            logger.warning("Seems like AuthMe is unable to find a proper spawn location. "
                                + "Set a location with the command '/authme setspawn'");
                        }
                        spawnLoc = world.getSpawnLocation();
                    }
                    break;
                default:
                    spawnLoc = getSpawnFromSource(source, world);
            }
            if (spawnLoc != null) {
                logger.debug("Spawn location determined as `{0}` for world `{1}`", spawnLoc, world.getName());
                return spawnLoc;
            }
        }
        logger.debug("Fall back to default world spawn location. World: `{0}`", world.getName());

        return world.getSpawnLocation(); // return default location
    }

    /**
     * Returns the spawn location for a single non-default source, or null when the source
     * is unknown, disabled, or has no spawn configured yet.
     */
    private Location getSpawnFromSource(String source, World world) {
        switch (source) {
            case "server":
                return getServerSpawnLocation(world);
            case "multiverse":
                return settings.getProperty(HooksSettings.MULTIVERSE)
                    ? pluginHookService.getMultiverseSpawn(world) : null;
            case "essentials":
                return essentialsSpawn;
            case "cmi":
                return cmiSpawn;
            case "authme":
                return getSpawn();
            default:
                return null;
        }
    }

    /**
     * Returns the first world with a valid spawn point, or null if none has one.
     */
    private World findValidSpawnWorld() {
        for (World spawnWorld : Bukkit.getWorlds()) {
            if (isValidSpawnPoint(spawnWorld.getSpawnLocation())) {
                return spawnWorld;
            }
        }
        return null;
    }

    /**
     * Returns a spawn location based on the world's native spawn, applying the {@code spawnRadius} gamerule
     * when its value is positive. This mimics vanilla behavior where players are placed at a random
     * position within the configured radius around the world spawn.
     *
     * @param world The world to retrieve the spawn point from
     * @return A location within spawnRadius of the world spawn, or the exact world spawn if radius is 0
     */
    private Location getServerSpawnLocation(World world) {
        Location worldSpawn = world.getSpawnLocation();
        int radius = compatibilityAdapter.getSpawnRadius(world);
        if (radius <= 0) {
            return worldSpawn;
        }
        int dx = (int) (Math.random() * (radius * 2 + 1)) - radius;
        int dz = (int) (Math.random() * (radius * 2 + 1)) - radius;
        int x = (int) worldSpawn.getX() + dx;
        int z = (int) worldSpawn.getZ() + dz;
        Integer y = getSafeSpawnY(world, x, z, worldSpawn.getBlockY());
        if (y == null) {
            return worldSpawn;
        }
        double spawnX = x + 0.5;
        double spawnZ = z + 0.5;
        float yaw = (float) Math.toDegrees(Math.atan2(-(worldSpawn.getX() - spawnX), worldSpawn.getZ() - spawnZ));
        return new Location(world, spawnX, y, spawnZ, yaw, 0.0f);
    }

    /**
     * Returns a safe Y coordinate for spawning at (x, z), searching near the world spawn's Y.
     * Checks if the foot and head blocks are passable at the base Y; if the foot is already
     * passable the search goes downward (looking for a floor), otherwise upward (looking for a gap).
     * A margin of 10 blocks is applied in each direction. Returns {@code null} if no safe spot
     * is found, in which case the caller should fall back to the exact world spawn location.
     */
    private Integer getSafeSpawnY(World world, int x, int z, int baseY) {
        if (isPassable(world, x, baseY, z) && isPassable(world, x, baseY + 1, z)) {
            return baseY;
        }
        int margin = 10;
        int direction = compatibilityAdapter.isBlockPassable(world.getBlockAt(x, baseY, z)) ? -1 : 1;
        for (int dy = 1; dy <= margin; dy++) {
            int y = baseY + dy * direction;
            if (isPassable(world, x, y, z) && isPassable(world, x, y + 1, z)) {
                return y;
            }
        }
        return null;
    }

    private boolean isPassable(World world, int x, int y, int z) {
        return compatibilityAdapter.isBlockPassable(world.getBlockAt(x, y, z));
    }

    /**
     * Checks if a given location is a valid spawn point [!= null && != (0,0,0)].
     *
     * @param location The location to check
     *
     * @return True upon success, false otherwise
     */
    private boolean isValidSpawnPoint(Location location) {
        return location != null
            && !(location.getX() == 0 && location.getY() == 0 && location.getZ() == 0);
    }

    /**
     * Save the location under the given prefix.
     *
     * @param prefix   The prefix to save the spawn under
     * @param location The location to persist
     *
     * @return True upon success, false otherwise
     */
    private boolean setLocation(String prefix, Location location) {
        if (location != null && location.getWorld() != null) {
            authMeConfiguration.set(prefix + ".world", location.getWorld().getName());
            authMeConfiguration.set(prefix + ".x", location.getX());
            authMeConfiguration.set(prefix + ".y", location.getY());
            authMeConfiguration.set(prefix + ".z", location.getZ());
            authMeConfiguration.set(prefix + ".yaw", location.getYaw());
            authMeConfiguration.set(prefix + ".pitch", location.getPitch());
            return saveAuthMeConfig();
        }
        return false;
    }

    private boolean saveAuthMeConfig() {
        try {
            authMeConfiguration.save(authMeConfigurationFile);
            return true;
        } catch (IOException e) {
            logger.logException("Could not save spawn config (" + authMeConfigurationFile + ")", e);
        }
        return false;
    }

    /**
     * Return player's location if player is alive, or player's spawn location if dead.
     *
     * @param player player to retrieve
     *
     * @return location of the given player if alive, spawn location if dead.
     */
    public Location getPlayerLocationOrSpawn(Player player) {
        if (player.getHealth() <= 0.0) {
            return getPlayerRespawnLocationOrSpawn(player);
        }
        return player.getLocation();
    }

    /**
     * Return the player's effective respawn location if available, or the configured spawn location as fallback.
     * <p>
     * We need this extra check for players who die and disconnect before actually respawning: in that case we want
     * to preserve the server's real respawn target instead of collapsing everything back to AuthMe's configured spawn.
     * The version-specific API call lives in {@link TeleportAdapter}: legacy builds use bed spawn, newer builds can
     * use {@code Player#getRespawnLocation()} directly.
     *
     * @param player player to retrieve
     * @return the player's respawn location if available, otherwise the configured spawn
     */
    public Location getPlayerRespawnLocationOrSpawn(Player player) {
        Location respawnLocation;
        try {
            respawnLocation = teleportAdapter.getPlayerRespawnLocation(player);
        } catch (RuntimeException e) {
            logger.debug("Could not read the respawn location of `{0}`, using the spawn instead: {1}",
                player.getName(), e.getMessage());
            respawnLocation = null;
        }
        if (respawnLocation != null && respawnLocation.getWorld() != null) {
            return respawnLocation;
        }
        return getSpawnLocation(player);
    }

    /**
     * Build a {@link Location} object from the given path in the file configuration.
     *
     * @param configuration The file configuration to read from
     * @param pathPrefix    The path to get the spawn point from
     *
     * @return Location corresponding to the values in the path
     */
    private static Location getLocationFromConfiguration(FileConfiguration configuration, String pathPrefix) {
        return buildLocation(configuration, pathPrefix, "world", "x", "y", "z", "yaw", "pitch");
    }

    /**
     * Build a {@link Location} from the fields stored under {@code pathPrefix}, using the given field keys.
     *
     * @param configuration the file configuration to read from
     * @param pathPrefix the path holding the spawn point
     * @param worldKey key of the world name
     * @param xKey key of the x coordinate
     * @param yKey key of the y coordinate
     * @param zKey key of the z coordinate
     * @param yawKey key of the yaw
     * @param pitchKey key of the pitch
     * @return the location, or null if the entry is incomplete
     */
    private static Location buildLocation(FileConfiguration configuration, String pathPrefix, String worldKey,
                                          String xKey, String yKey, String zKey, String yawKey, String pitchKey) {
        if (!containsAllFields(configuration, pathPrefix, worldKey, xKey, yKey, zKey, yawKey, pitchKey)) {
            return null;
        }
        String prefix = pathPrefix + ".";
        String worldName = configuration.getString(prefix + worldKey);
        World world = Bukkit.getWorld(worldName);
        if (StringUtils.isBlank(worldName) || world == null) {
            return null;
        }
        return new Location(world, configuration.getDouble(prefix + xKey),
            configuration.getDouble(prefix + yKey), configuration.getDouble(prefix + zKey),
            getFloat(configuration, prefix + yawKey), getFloat(configuration, prefix + pitchKey));
    }

    /**
     * Return whether the file configuration contains all given fields under the given path.
     *
     * @param configuration The file configuration to use
     * @param pathPrefix    The path to verify
     * @param fields        The field names to look for
     *
     * @return True if all fields are present, false otherwise
     */
    private static boolean containsAllFields(FileConfiguration configuration, String pathPrefix, String... fields) {
        for (String field : fields) {
            if (!configuration.contains(pathPrefix + "." + field)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Retrieve a property as a float from the given file configuration.
     *
     * @param configuration The file configuration to use
     * @param path          The path of the property to retrieve
     *
     * @return The float
     */
    private static float getFloat(FileConfiguration configuration, String path) {
        Object value = configuration.get(path);
        // This behavior is consistent with FileConfiguration#getDouble
        return (value instanceof Number) ? ((Number) value).floatValue() : 0;
    }

}
