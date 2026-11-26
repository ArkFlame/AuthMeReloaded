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

    static {
        Method direct = findMethod(Player.class, "getLocale");
        Method spigot = direct == null ? findMethod(Player.class, "spigot") : null;
        PLAYER_GET_LOCALE_METHOD = direct;
        PLAYER_SPIGOT_METHOD = spigot;
        SPIGOT_GET_LOCALE_METHOD = spigot == null ? null : findMethod(spigot.getReturnType(), "getLocale");
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

    /**
     * Returns the scheduler provider resolved for the running server. Subclasses can delegate
     * global and asynchronous work here instead of keeping a second provider instance.
     *
     * @return the shared scheduler provider
     */
    protected SchedulerProvider getScheduler() {
        return scheduler;
    }

    @Override
    public Optional<String> getPlayerLocale(Player player) {
        try {
            String locale = null;
            if (PLAYER_GET_LOCALE_METHOD != null) {
                Object result = PLAYER_GET_LOCALE_METHOD.invoke(player);
                if (result instanceof String) {
                    locale = (String) result;
                }
            } else if (PLAYER_SPIGOT_METHOD != null && SPIGOT_GET_LOCALE_METHOD != null) {
                Object spigot = PLAYER_SPIGOT_METHOD.invoke(player);
                Object result = spigot == null ? null : SPIGOT_GET_LOCALE_METHOD.invoke(spigot);
                if (result instanceof String) {
                    locale = (String) result;
                }
            }
            return locale == null || locale.trim().isEmpty() ? Optional.empty() : Optional.of(locale);
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
                // fallback to material solidity check
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

        String[][] optionalListeners = {
            {"org.spigotmc.event.player.PlayerSpawnLocationEvent", "fr.xephi.authme.listener.LegacyPlayerSpawnLocationListener"},
            {"org.bukkit.event.player.PlayerSwapHandItemsEvent", "fr.xephi.authme.listener.PlayerSwapHandItemsListener"},
            {"org.bukkit.event.entity.EntityAirChangeEvent", "fr.xephi.authme.listener.EntityAirChangeListener"},
            {"org.bukkit.event.entity.EntityPickupItemEvent", "fr.xephi.authme.listener.EntityPickupItemListener"},
            {"org.bukkit.event.player.PlayerPickupArrowEvent", "fr.xephi.authme.listener.PlayerPickupArrowListener"}
        };
        for (String[] entry : optionalListeners) {
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
    private PacketInterceptionAdapter packetInterceptionAdapter;

    @Override
    public void registerInventoryProtection(PlayerCache playerCache, DataSource dataSource) {
        getOrCreatePacketInterceptionAdapter().registerInventoryProtection(playerCache, dataSource);
    }

    @Override
    public void unregisterInventoryProtection() {
        packetAdapter().ifPresent(PacketInterceptionAdapter::unregisterInventoryProtection);
    }

    @Override
    public void sendBlankInventoryPacket(Player player) {
        packetAdapter().ifPresent(adapter -> adapter.sendBlankInventoryPacket(player));
    }

    @Override
    public void registerTabCompleteBlock(PlayerCache playerCache) {
        getOrCreatePacketInterceptionAdapter().registerTabCompleteBlock(playerCache);
    }

    @Override
    public void unregisterTabCompleteBlock() {
        packetAdapter().ifPresent(PacketInterceptionAdapter::unregisterTabCompleteBlock);
    }

    @Override
    public void registerPremiumVerification(DataSource dataSource, PremiumLoginVerifier verifier,
                                            PendingPremiumCache pendingPremiumCache, BukkitService bukkitService) {
        getOrCreatePacketInterceptionAdapter()
            .registerPremiumVerification(dataSource, verifier, pendingPremiumCache, bukkitService);
    }

    @Override
    public void unregisterPremiumVerification() {
        packetAdapter().ifPresent(PacketInterceptionAdapter::unregisterPremiumVerification);
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
        for (String className : requiredClasses) {
            if (!Utils.isClassLoaded(className)) {
                return errorMessage;
            }
        }
        return null;
    }

    /** Returns the lazily created packet adapter only if it has been requested already. */
    private Optional<PacketInterceptionAdapter> packetAdapter() {
        return Optional.ofNullable(packetInterceptionAdapter);
    }

    private PacketInterceptionAdapter getOrCreatePacketInterceptionAdapter() {
        if (packetInterceptionAdapter == null) {
            packetInterceptionAdapter = createPacketInterceptionAdapter();
        }
        return packetInterceptionAdapter;
    }
}
