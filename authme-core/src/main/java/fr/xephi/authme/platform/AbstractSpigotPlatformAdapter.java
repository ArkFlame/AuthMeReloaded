package fr.xephi.authme.platform;

import fr.xephi.authme.AuthMe;
import fr.xephi.authme.listener.AdvancedShulkerFixListener;
import fr.xephi.authme.listener.LegacyPlayerLoginListener;
import fr.xephi.authme.listener.LegacyPlayerPickupItemListener;
import fr.xephi.authme.data.auth.PlayerCache;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.listener.packetevents.PacketEventsListenerRegistry;
import fr.xephi.authme.service.BukkitService;
import fr.xephi.authme.service.CancellableTask;
import fr.xephi.authme.service.PendingPremiumCache;
import fr.xephi.authme.service.PremiumLoginVerifier;
import fr.xephi.authme.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Base implementation of {@link PlatformAdapter} for all Spigot versions.
 * Uses synchronous (blocking) teleport via the Bukkit API.
 */
public abstract class AbstractSpigotPlatformAdapter implements PlatformAdapter {

    private static final Method PLAYER_GET_LOCALE_METHOD;
    private static final Method PLAYER_SPIGOT_METHOD;
    private static final Method SPIGOT_GET_LOCALE_METHOD;
    private static final Method BLOCK_IS_PASSABLE_METHOD;
    private static final String[][] OPTIONAL_LISTENERS = {
        {"org.spigotmc.event.player.PlayerSpawnLocationEvent", "fr.xephi.authme.listener.LegacyPlayerSpawnLocationListener"},
        {"org.bukkit.event.player.PlayerSwapHandItemsEvent", "fr.xephi.authme.listener.PlayerSwapHandItemsListener"},
        {"org.bukkit.event.entity.EntityAirChangeEvent", "fr.xephi.authme.listener.EntityAirChangeListener"},
        {"org.bukkit.event.entity.EntityPickupItemEvent", "fr.xephi.authme.listener.EntityPickupItemListener"},
        {"org.bukkit.event.player.PlayerPickupArrowEvent", "fr.xephi.authme.listener.PlayerPickupArrowListener"}
    };

    static {
        PLAYER_GET_LOCALE_METHOD = findMethod(Player.class, "getLocale");
        PLAYER_SPIGOT_METHOD = PLAYER_GET_LOCALE_METHOD == null ? findMethod(Player.class, "spigot") : null;
        SPIGOT_GET_LOCALE_METHOD = PLAYER_SPIGOT_METHOD == null
            ? null : findMethod(PLAYER_SPIGOT_METHOD.getReturnType(), "getLocale");
        BLOCK_IS_PASSABLE_METHOD = findMethod(Block.class, "isPassable");
    }

    private static Method findMethod(Class<?> owner, String name) {
        try {
            return owner.getMethod(name);
        } catch (Exception ignored) {
            return null;
        }
    }

    /** The scheduler matching the running server; keeps Spigot and Folia scheduling consistent. */
    private final SchedulerProvider scheduler = SchedulerProviders.current();

    @Override
    public Optional<String> getPlayerLocale(Player player) {
        try {
            Object result = PLAYER_GET_LOCALE_METHOD == null ? null : PLAYER_GET_LOCALE_METHOD.invoke(player);
            if (result == null && SPIGOT_GET_LOCALE_METHOD != null) {
                Object spigot = PLAYER_SPIGOT_METHOD.invoke(player);
                result = spigot == null ? null : SPIGOT_GET_LOCALE_METHOD.invoke(spigot);
            }
            String locale = result instanceof String ? (String) result : null;
            return locale != null && !locale.trim().isEmpty() ? Optional.of(locale) : Optional.empty();
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    @Override
    public int getSpawnRadius(World world) {
        try {
            String value = world.getGameRuleValue("spawnRadius");
            return value == null ? 0 : Math.max(0, Integer.parseInt(value));
        } catch (Exception ignored) {
            return 0;
        }
    }

    @Override
    public boolean isBlockPassable(Block block) {
        if (BLOCK_IS_PASSABLE_METHOD != null) {
            try {
                return (Boolean) BLOCK_IS_PASSABLE_METHOD.invoke(block);
            } catch (Exception ignored) {
            }
        }
        return !block.getType().isSolid();
    }

    protected boolean isClassAvailable(String className) {
        return Utils.isClassLoaded(className);
    }

    @Override
    public List<Class<? extends Listener>> getListeners() {
        List<Class<? extends Listener>> listeners = new ArrayList<>(EventRegistrationAdapter.getCommonListeners());
        listeners.add(LegacyPlayerLoginListener.class);

        if (!isClassAvailable("org.bukkit.event.entity.EntityPickupItemEvent")) {
            listeners.add(LegacyPlayerPickupItemListener.class);
        }

        for (String[] entry : OPTIONAL_LISTENERS) {
            if (isClassAvailable(entry[0])) {
                try {
                    Class<? extends Listener> clazz = Class.forName(entry[1], false, getClass().getClassLoader())
                        .asSubclass(Listener.class);
                    listeners.add(clazz);
                } catch (ClassNotFoundException e) {
                    throw new IllegalStateException("Missing listener class: " + entry[1], e);
                }
            }
        }
        // PlayerCommandSendEvent arrived in 1.13; the shulker fix is for 1.8 only.
        if (!isClassAvailable("org.bukkit.event.player.PlayerCommandSendEvent")) {
            listeners.add(AdvancedShulkerFixListener.class);
        }
        return Collections.unmodifiableList(listeners);
    }

    @Override
    public void teleportPlayer(Player player, Location location) {
        player.teleport(location);
    }

    @Override
    public Location getPlayerRespawnLocation(Player player) {
        return player.getBedSpawnLocation();
    }

    @Override
    public boolean isOwnedByCurrentThread(Entity entity) {
        return isGlobalThread();
    }

    @Override
    public boolean isOwnedByCurrentThread(Location location) {
        return isGlobalThread();
    }

    @Override
    public boolean isGlobalThread() {
        return scheduler.isGlobalThread();
    }

    @Override
    public void runOnEntityThread(AuthMe plugin, Entity entity, Runnable task) {
        runOnGlobalThread(plugin, task);
    }

    @Override
    public CancellableTask runDelayedOnEntityThread(AuthMe plugin, Entity entity, Runnable task, long delay) {
        return runDelayedOnGlobalThread(plugin, task, delay);
    }

    @Override
    public CancellableTask runAtFixedRateOnEntityThread(AuthMe plugin, Entity entity, Runnable task,
                                                        long delay, long period) {
        return scheduler.runAtFixedRateOnGlobalThread(plugin, task, delay, period);
    }

    @Override
    public CancellableTask runAsyncTask(AuthMe plugin, Runnable task) {
        return scheduler.runAsync(plugin, task);
    }

    @Override
    public CancellableTask runAsyncTaskTimer(AuthMe plugin, Runnable task, long delay, long period) {
        return scheduler.runAsyncTimer(plugin, task, delay, period);
    }

    @Override
    public void runOnGlobalThread(AuthMe plugin, Runnable task) {
        scheduler.runOnGlobalThread(plugin, task);
    }

    @Override
    public CancellableTask runDelayedOnGlobalThread(AuthMe plugin, Runnable task, long delay) {
        return scheduler.runDelayedOnGlobalThread(plugin, task, delay);
    }

    // Kept lazy so PacketEvents-dependent classes are only loaded after PacketEvents has been confirmed present.
    private volatile PacketInterceptionAdapter packetInterceptionAdapter;

    @Override
    public void registerInventoryProtection(PlayerCache playerCache, DataSource dataSource) {
        getOrCreatePacketInterceptionAdapter().registerInventoryProtection(playerCache, dataSource);
    }

    @Override
    public void unregisterInventoryProtection() {
        runIfAdapterPresent(() -> packetInterceptionAdapter.unregisterInventoryProtection());
    }

    @Override
    public void sendBlankInventoryPacket(Player player) {
        runIfAdapterPresent(() -> packetInterceptionAdapter.sendBlankInventoryPacket(player));
    }

    @Override
    public void registerTabCompleteBlock(PlayerCache playerCache) {
        getOrCreatePacketInterceptionAdapter().registerTabCompleteBlock(playerCache);
    }

    @Override
    public void unregisterTabCompleteBlock() {
        runIfAdapterPresent(() -> packetInterceptionAdapter.unregisterTabCompleteBlock());
    }

    @Override
    public void registerPremiumVerification(DataSource dataSource, PremiumLoginVerifier verifier,
                                            PendingPremiumCache pendingPremiumCache, BukkitService bukkitService) {
        getOrCreatePacketInterceptionAdapter()
            .registerPremiumVerification(dataSource, verifier, pendingPremiumCache, bukkitService);
    }

    @Override
    public void unregisterPremiumVerification() {
        runIfAdapterPresent(() -> packetInterceptionAdapter.unregisterPremiumVerification());
    }

    @Override
    public boolean isProxyForwardingEnabled() {
        try {
            return Bukkit.getServer().spigot().getConfig()
                .getBoolean("settings.bungeecord", false);
        } catch (Exception | LinkageError ignored) {
            return false;
        }
    }

    protected PacketInterceptionAdapter createPacketInterceptionAdapter() {
        return new PacketEventsListenerRegistry();
    }

    protected final String getCompatibilityError(String errorMessage, String... requiredClasses) {
        return Arrays.stream(requiredClasses).allMatch(Utils::isClassLoaded) ? null : errorMessage;
    }

    private PacketInterceptionAdapter getOrCreatePacketInterceptionAdapter() {
        if (packetInterceptionAdapter == null) {
            packetInterceptionAdapter = createPacketInterceptionAdapter();
        }
        return packetInterceptionAdapter;
    }

    private void runIfAdapterPresent(Runnable action) {
        if (packetInterceptionAdapter != null) {
            action.run();
        }
    }
}
