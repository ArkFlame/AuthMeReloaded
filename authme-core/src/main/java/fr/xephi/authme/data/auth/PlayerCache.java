package fr.xephi.authme.data.auth;


import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Used to manage player's Authenticated status with a concurrent map and a shared read-only view.
 */
public class PlayerCache {

    private final ConcurrentMap<String, PlayerAuth> cache = new ConcurrentHashMap<>();
    // One wrapper for the lifetime of the cache rather than a new one (or a full copy) per getCache() call.
    private final Map<String, PlayerAuth> readOnlyCache = Collections.unmodifiableMap(cache);

    PlayerCache() {
    }

    /**
     * Adds the given auth object to the player cache (for the name defined in the PlayerAuth).
     *
     * @param auth the player auth object to save
     */
    public void updatePlayer(PlayerAuth auth) {
        cache.put(key(auth.getNickname()), auth);
    }

    /**
     * Replaces the auth object of a player already in the cache, without adding a player who is not.
     *
     * @param auth the player auth object to save
     */
    public void updatePlayerIfPresent(PlayerAuth auth) {
        // replace(K, V) only replaces an existing entry and avoids a capturing lambda.
        cache.replace(key(auth.getNickname()), auth);
    }

    /**
     * Removes a player from the player cache.
     *
     * @param user name of the player to remove
     */
    public void removePlayer(String user) {
        cache.remove(key(user));
    }

    /**
     * Get whether a player is authenticated (i.e. whether he is present in the player cache).
     *
     * @param user player's name
     *
     * @return true if player is logged in, false otherwise.
     */
    public boolean isAuthenticated(String user) {
        return cache.containsKey(key(user));
    }

    /**
     * Returns the PlayerAuth associated with the given user, if available.
     *
     * @param user name of the player
     *
     * @return the associated auth object, or null if not available
     */
    public PlayerAuth getAuth(String user) {
        return cache.get(key(user));
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    /**
     * @return number of logged in players
     */
    public int getLogged() {
        return cache.size();
    }

    /**
     * Returns the player cache data as a read-only view.
     *
     * @return all player auths inside the player cache
     */
    public Map<String, PlayerAuth> getCache() {
        return readOnlyCache;
    }

}
