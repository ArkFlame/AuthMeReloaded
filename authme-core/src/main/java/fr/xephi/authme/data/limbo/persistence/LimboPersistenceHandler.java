package fr.xephi.authme.data.limbo.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import fr.xephi.authme.data.limbo.LimboPlayer;
import fr.xephi.authme.service.BukkitService;
import org.bukkit.entity.Player;

/**
 * Handles I/O for storing LimboPlayer objects.
 */
interface LimboPersistenceHandler {

    /**
     * Returns the limbo player for the given player if it exists.
     *
     * @param player the player
     * @return the stored limbo player, or null if not available
     */
    LimboPlayer getLimboPlayer(Player player);

    /**
     * Saves the given limbo player for the given player to the disk.
     *
     * @param player the player to save the limbo player for
     * @param limbo the limbo player to save
     */
    void saveLimboPlayer(Player player, LimboPlayer limbo);

    /**
     * Removes the limbo player from the disk.
     *
     * @param player the player whose limbo player should be removed
     */
    void removeLimboPlayer(Player player);

    /**
     * @return the type of the limbo persistence implementation
     */
    LimboPersistenceType getType();

    /** Creates the Gson instance used to read and write LimboPlayer objects. */
    static Gson createGson(BukkitService bukkitService) {
        return new GsonBuilder()
            .registerTypeAdapter(LimboPlayer.class, new LimboPlayerSerializer())
            .registerTypeAdapter(LimboPlayer.class, new LimboPlayerDeserializer(bukkitService))
            .setPrettyPrinting()
            .create();
    }

}
