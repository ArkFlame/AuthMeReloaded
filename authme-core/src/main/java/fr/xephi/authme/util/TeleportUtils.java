package fr.xephi.authme.util;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.CompletableFuture;

/**
 * Utility for teleporting players, preferring the asynchronous API when the server supports it.
 *
 * <p>Ported from HaHaWTH/AuthMeReReloaded.</p>
 */
public final class TeleportUtils {

    private static final MethodHandle TELEPORT_ASYNC_METHOD = findTeleportAsyncMethod();

    private TeleportUtils() {
    }

    /**
     * Teleports a player to the given location.
     *
     * @param player the player to teleport
     * @param location the destination
     */
    public static void teleport(Player player, Location location) {
        if (TELEPORT_ASYNC_METHOD != null) {
            try {
                TELEPORT_ASYNC_METHOD.invoke(player, location);
                return;
            } catch (Throwable ignored) {
                // Fall back to the synchronous teleport below
            }
        }
        player.teleport(location);
    }

    private static MethodHandle findTeleportAsyncMethod() {
        try {
            return MethodHandles.lookup().findVirtual(Player.class, "teleportAsync",
                MethodType.methodType(CompletableFuture.class, Location.class));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            return null;
        }
    }
}
