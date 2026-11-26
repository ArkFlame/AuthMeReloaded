package fr.xephi.authme.platform;

import fr.xephi.authme.service.CancellableTask;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * {@link SchedulerProvider} for Folia's region-threaded servers.
 *
 * <p>Folia's scheduler types are not part of the Bukkit API that core compiles against,
 * so every Folia call is made reflectively. This keeps core Java 8 and Spigot 1.8 safe
 * while still using the correct region and async schedulers at runtime. Instances are
 * only created when {@link SchedulerProviders#isFolia()} detects the Folia API.
 */
final class FoliaSchedulerProvider implements SchedulerProvider {

    /** One Minecraft tick lasts 50 milliseconds. */
    private static final long MILLIS_PER_TICK = 50L;

    private static final CancellableTask NO_OP_TASK = () -> { };

    private volatile Object globalScheduler;
    private boolean globalSchedulerResolved;
    private volatile Object asyncScheduler;
    private boolean asyncSchedulerResolved;

    @Override
    public boolean isFolia() {
        return true;
    }

    @Override
    public boolean isGlobalThread() {
        Method check = findMethod(Bukkit.class, "isGlobalTickThread");
        // Bukkit#isPrimaryThread throws on Folia, so never use it as a fallback here.
        return check != null && Boolean.TRUE.equals(invoke(check, null));
    }

    @Override
    public void runOnGlobalThread(Plugin plugin, Runnable task) {
        Object scheduler = getGlobalScheduler();
        Method run = findMethod(scheduler, "run", Plugin.class, Consumer.class);
        if (run == null) {
            warnUnavailable(plugin, "global region scheduler");
            return;
        }
        invoke(run, scheduler, plugin, toConsumer(task));
    }

    @Override
    public CancellableTask runDelayedOnGlobalThread(Plugin plugin, Runnable task, long delayTicks) {
        Object scheduler = getGlobalScheduler();
        Method runDelayed = findMethod(scheduler, "runDelayed", Plugin.class, Consumer.class, long.class);
        if (runDelayed == null) {
            warnUnavailable(plugin, "global region scheduler");
            return NO_OP_TASK;
        }
        return asCancellable(invoke(runDelayed, scheduler, plugin, toConsumer(task), delayTicks));
    }

    @Override
    public CancellableTask runAtFixedRateOnGlobalThread(Plugin plugin, Runnable task,
                                                        long delayTicks, long periodTicks) {
        Object scheduler = getGlobalScheduler();
        Method runAtFixedRate = findMethod(scheduler, "runAtFixedRate", Plugin.class, Consumer.class,
            long.class, long.class, TimeUnit.class);
        if (runAtFixedRate == null) {
            warnUnavailable(plugin, "global region scheduler");
            return NO_OP_TASK;
        }
        return asCancellable(invoke(runAtFixedRate, scheduler, plugin, toConsumer(task),
            delayTicks * MILLIS_PER_TICK, periodTicks * MILLIS_PER_TICK, TimeUnit.MILLISECONDS));
    }

    @Override
    public CancellableTask runAsync(Plugin plugin, Runnable task) {
        Object scheduler = getAsyncScheduler();
        Method runNow = findMethod(scheduler, "runNow", Plugin.class, Consumer.class);
        if (runNow == null) {
            warnUnavailable(plugin, "async scheduler");
            return NO_OP_TASK;
        }
        return asCancellable(invoke(runNow, scheduler, plugin, toConsumer(task)));
    }

    @Override
    public CancellableTask runAsyncTimer(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        Object scheduler = getAsyncScheduler();
        Method runAtFixedRate = findMethod(scheduler, "runAtFixedRate", Plugin.class, Consumer.class,
            long.class, long.class, TimeUnit.class);
        if (runAtFixedRate == null) {
            warnUnavailable(plugin, "async scheduler");
            return NO_OP_TASK;
        }
        return asCancellable(invoke(runAtFixedRate, scheduler, plugin, toConsumer(task),
            delayTicks * MILLIS_PER_TICK, periodTicks * MILLIS_PER_TICK, TimeUnit.MILLISECONDS));
    }

    private Object getGlobalScheduler() {
        if (!globalSchedulerResolved) {
            synchronized (this) {
                if (!globalSchedulerResolved) {
                    globalScheduler = getBukkitScheduler("getGlobalRegionScheduler");
                    globalSchedulerResolved = true;
                }
            }
        }
        return globalScheduler;
    }

    private Object getAsyncScheduler() {
        if (!asyncSchedulerResolved) {
            synchronized (this) {
                if (!asyncSchedulerResolved) {
                    asyncScheduler = getBukkitScheduler("getAsyncScheduler");
                    asyncSchedulerResolved = true;
                }
            }
        }
        return asyncScheduler;
    }

    private static Object getBukkitScheduler(String getterName) {
        Method getter = findMethod(Bukkit.class, getterName);
        return getter == null ? null : invoke(getter, null);
    }

    private static Method findMethod(Object receiver, String name, Class<?>... parameterTypes) {
        if (receiver == null) {
            return null;
        }
        try {
            return receiver.getClass().getMethod(name, parameterTypes);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static Method findMethod(Class<?> owner, String name, Class<?>... parameterTypes) {
        try {
            return owner.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static Object invoke(Method method, Object receiver, Object... arguments) {
        try {
            return method.invoke(receiver, arguments);
        } catch (Exception e) {
            return null;
        }
    }

    private static Consumer<Object> toConsumer(Runnable task) {
        return ignored -> task.run();
    }

    private static CancellableTask asCancellable(Object scheduledTask) {
        if (scheduledTask == null) {
            return NO_OP_TASK;
        }
        Method cancel = findMethod(scheduledTask, "cancel");
        if (cancel == null) {
            return NO_OP_TASK;
        }
        return () -> invoke(cancel, scheduledTask);
    }

    private static void warnUnavailable(Plugin plugin, String schedulerName) {
        plugin.getLogger().warning("Folia " + schedulerName + " is unavailable; skipping scheduled task");
    }
}
