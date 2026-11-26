package fr.xephi.authme.service;

import ch.jalu.injector.annotations.NoFieldScan;
import com.earth2me.essentials.Essentials;
import com.onarandombox.MultiverseCore.MultiverseCore;
import com.onarandombox.MultiverseCore.api.MVWorldManager;
import fr.xephi.authme.ConsoleLogger;
import fr.xephi.authme.output.ConsoleLoggerFactory;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import javax.inject.Inject;
import java.io.File;
import java.util.function.Supplier;

/**
 * Hooks into third-party plugins and allows to perform actions on them.
 */
@NoFieldScan
public class PluginHookService {

    private final ConsoleLogger logger = ConsoleLoggerFactory.get(PluginHookService.class);
    private final PluginManager pluginManager;
    private Essentials essentials;
    private Plugin cmi;
    private MultiverseCore multiverse;

    /**
     * Constructor.
     *
     * @param pluginManager The server's plugin manager
     */
    @Inject
    public PluginHookService(PluginManager pluginManager) {
        this.pluginManager = pluginManager;
        tryHookToEssentials();
        tryHookToCmi();
        tryHookToMultiverse();
    }

    /**
     * Enable or disable the social spy status of the given user if Essentials is available.
     *
     * @param player The player to modify
     * @param socialSpyStatus The social spy status (enabled/disabled) to set
     */
    public void setEssentialsSocialSpyStatus(Player player, boolean socialSpyStatus) {
        if (essentials != null) {
            essentials.getUser(player).setSocialSpyEnabled(socialSpyStatus);
        }
    }

    /**
     * @return The Essentials data folder, or null if unavailable
     */
    public File getEssentialsDataFolder() {
        return essentials == null ? null : essentials.getDataFolder();
    }

    /**
     * @return The CMI data folder, or null if unavailable
     */
    public File getCmiDataFolder() {
        Plugin plugin = pluginManager.getPlugin("CMI");
        return plugin == null ? null : plugin.getDataFolder();
    }

    /**
     * Return the spawn of the given world as defined by Multiverse (if available).
     *
     * @param world The world to get the Multiverse spawn for
     * @return The spawn location from Multiverse, or null if unavailable
     */
    public Location getMultiverseSpawn(World world) {
        if (multiverse == null) {
            return null;
        }
        MVWorldManager manager = multiverse.getMVWorldManager();
        return manager.isMVWorld(world) ? manager.getMVWorld(world).getSpawnLocation() : null;
    }

    public boolean isEssentialsAvailable() {
        return essentials != null;
    }

    public boolean isCmiAvailable() {
        return cmi != null;
    }

    public boolean isMultiverseAvailable() {
        return multiverse != null;
    }

    public void tryHookToEssentials() {
        essentials = tryHook("Essentials", () -> Essentials.class);
    }

    public void tryHookToCmi() {
        cmi = tryHook("CMI", () -> Plugin.class);
    }

    public void tryHookToMultiverse() {
        multiverse = tryHook("Multiverse-Core", () -> MultiverseCore.class);
    }

    public void unhookEssentials() {
        essentials = null;
    }

    public void unhookCmi() {
        cmi = null;
    }

    public void unhookMultiverse() {
        multiverse = null;
    }

    private <T extends Plugin> T tryHook(String name, Supplier<Class<T>> clazz) {
        try {
            if (pluginManager.isPluginEnabled(name)) {
                T plugin = clazz.get().cast(pluginManager.getPlugin(name));
                logger.info("Hooked successfully into " + name);
                return plugin;
            }
        } catch (Exception | LinkageError ignored) {
            // Plugin is not available
        }
        return null;
    }
}
