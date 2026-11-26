package fr.xephi.authme.data.auth;


import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Used to manage player's Authenticated status
 */
public class PlayerCache {

    private final Map<String, PlayerAuth> cache = new ConcurrentHashMap<>();

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
        // replace(K, V) has the same "only if already present" semantics as
        // computeIfPresent but avoids allocating a capturing lambda on every call.
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
     * Returns the player cache data.
     *
     * @return all player auths inside the player cache
     */
    public Map<String, PlayerAuth> getCache() {
        return this.cache;
    }

}
