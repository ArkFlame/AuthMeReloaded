package fr.xephi.authme.platform;

import fr.xephi.authme.service.CancellableTask;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * {@link SchedulerProvider} for Folia's region-threaded servers. Folia's scheduler types are not
 * part of the Bukkit API core compiles against, so every Folia call is made reflectively. This
 * keeps core Java 8 and Spigot 1.8 safe. Instances are only created when
 * {@link SchedulerProviders#isFolia()} detects the Folia API.
 */
final class FoliaSchedulerProvider implements SchedulerProvider {

    /** One Minecraft tick lasts 50 milliseconds. */
    private static final long MILLIS_PER_TICK = 50L;

    private static final CancellableTask NO_OP_TASK = () -> { };

    private static final String GLOBAL_SCHEDULER_NAME = "global region scheduler";
    private static final String ASYNC_SCHEDULER_NAME = "async scheduler";

    private static final Method IS_GLOBAL_TICK_THREAD = findMethod(Bukkit.class, "isGlobalTickThread");

    private final LazyScheduler globalScheduler = new LazyScheduler("getGlobalRegionScheduler");
    private final LazyScheduler asyncScheduler = new LazyScheduler("getAsyncScheduler");

    @Override
    public boolean isGlobalThread() {
        // Bukkit#isPrimaryThread throws on Folia, so never use it as a fallback here.
        return IS_GLOBAL_TICK_THREAD != null && Boolean.TRUE.equals(invoke(IS_GLOBAL_TICK_THREAD, null));
    }

    @Override
    public void runOnGlobalThread(Plugin plugin, Runnable task) {
        runOnce(globalScheduler, "run", GLOBAL_SCHEDULER_NAME, plugin, task);
    }

    @Override
    public CancellableTask runDelayedOnGlobalThread(Plugin plugin, Runnable task, long delayTicks) {
        Object scheduler = globalScheduler.get();
        return schedule(plugin, scheduler,
            findMethod(scheduler, "runDelayed", Plugin.class, Consumer.class, long.class),
            GLOBAL_SCHEDULER_NAME, plugin, toConsumer(task), delayTicks);
    }

    @Override
    public CancellableTask runAtFixedRateOnGlobalThread(Plugin plugin, Runnable task,
                                                        long delayTicks, long periodTicks) {
        return scheduleRepeating(globalScheduler.get(), plugin, task, delayTicks, periodTicks, GLOBAL_SCHEDULER_NAME);
    }

    @Override
    public CancellableTask runAsync(Plugin plugin, Runnable task) {
        return runOnce(asyncScheduler, "runNow", ASYNC_SCHEDULER_NAME, plugin, task);
    }

    @Override
    public CancellableTask runAsyncTimer(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        return scheduleRepeating(asyncScheduler.get(), plugin, task, delayTicks, periodTicks, ASYNC_SCHEDULER_NAME);
    }

    /** Schedules a one-shot Folia task on the given lazily resolved scheduler. */
    private CancellableTask runOnce(LazyScheduler lazy, String methodName, String schedulerName,
                                    Plugin plugin, Runnable task) {
        Object scheduler = lazy.get();
        return schedule(plugin, scheduler,
            findMethod(scheduler, methodName, Plugin.class, Consumer.class),
            schedulerName, plugin, toConsumer(task));
    }

    /** Schedules a repeating Folia task, converting tick delays and periods to milliseconds. */
    private CancellableTask scheduleRepeating(Object scheduler, Plugin plugin, Runnable task,
                                              long delayTicks, long periodTicks, String schedulerName) {
        return schedule(plugin, scheduler,
            findMethod(scheduler, "runAtFixedRate", Plugin.class, Consumer.class,
                long.class, long.class, TimeUnit.class),
            schedulerName, plugin, toConsumer(task),
            delayTicks * MILLIS_PER_TICK, periodTicks * MILLIS_PER_TICK, TimeUnit.MILLISECONDS);
    }

    /** Resolves a Folia scheduler from Bukkit on first use and caches the result. */
    private static final class LazyScheduler {

        private final String getterName;
        private volatile Object value;
        private volatile boolean resolved;

        private LazyScheduler(String getterName) {
            this.getterName = getterName;
        }

        Object get() {
            if (!resolved) {
                synchronized (this) {
                    if (!resolved) {
                        Method getter = findMethod(Bukkit.class, getterName);
                        value = getter == null ? null : invoke(getter, null);
                        resolved = true;
                    }
                }
            }
            return value;
        }
    }

    /** Resolves a public method on a class or instance receiver, returning null if absent. */
    private static Method findMethod(Object receiver, String name, Class<?>... parameterTypes) {
        if (receiver == null) {
            return null;
        }
        Class<?> owner = receiver instanceof Class<?> ? (Class<?>) receiver : receiver.getClass();
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

    /** Invokes a resolved scheduler method, or warns and returns a no-op handle when unavailable. */
    private static CancellableTask schedule(Plugin plugin, Object scheduler, Method method,
                                            String schedulerName, Object... arguments) {
        if (method == null) {
            plugin.getLogger().warning("Folia " + schedulerName + " is unavailable; skipping scheduled task");
            return NO_OP_TASK;
        }
        Object scheduledTask;
        try {
            scheduledTask = method.invoke(scheduler, arguments);
        } catch (Exception e) {
            plugin.getLogger().warning("Folia " + schedulerName + " rejected a task: " + e);
            return NO_OP_TASK;
        }
        Method cancel = findMethod(scheduledTask, "cancel");
        return cancel == null ? NO_OP_TASK : () -> invoke(cancel, scheduledTask);
    }
}
