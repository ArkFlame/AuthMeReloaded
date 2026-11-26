package fr.xephi.authme.data.limbo;

import fr.xephi.authme.ConsoleLogger;
import fr.xephi.authme.output.ConsoleLoggerFactory;
import org.bukkit.entity.Player;

/**
 * Possible types to restore the walk and fly speed from LimboPlayer
 * back to Bukkit Player.
 */
public enum WalkFlySpeedRestoreType {

    /** Restores from LimboPlayer to Player. */
    RESTORE {
        @Override
        public void restoreFlySpeed(Player player, LimboPlayer limbo) {
            float speed = limbo.getFlySpeed();
            log(player, true, String.valueOf(speed), " (RESTORE mode)");
            setSpeed(player, true, speed);
        }

        @Override
        public void restoreWalkSpeed(Player player, LimboPlayer limbo) {
            float speed = limbo.getWalkSpeed();
            log(player, false, String.valueOf(speed), " (RESTORE mode)");
            setSpeed(player, false, speed);
        }
    },

    /** Restores from LimboPlayer, using the default speed if the speed on LimboPlayer is 0. */
    RESTORE_NO_ZERO {
        @Override
        public void restoreFlySpeed(Player player, LimboPlayer limbo) {
            restoreNoZero(player, limbo, true);
        }

        @Override
        public void restoreWalkSpeed(Player player, LimboPlayer limbo) {
            restoreNoZero(player, limbo, false);
        }
    },

    /** Uses the max speed of Player (current speed) and the LimboPlayer. */
    MAX_RESTORE {
        @Override
        public void restoreFlySpeed(Player player, LimboPlayer limbo) {
            restoreMax(player, limbo, true);
        }

        @Override
        public void restoreWalkSpeed(Player player, LimboPlayer limbo) {
            restoreMax(player, limbo, false);
        }
    },

    /** Always sets the default speed to the player. */
    DEFAULT {
        @Override
        public void restoreFlySpeed(Player player, LimboPlayer limbo) {
            restoreDefault(player, true);
        }

        @Override
        public void restoreWalkSpeed(Player player, LimboPlayer limbo) {
            restoreDefault(player, false);
        }
    };

    private static final ConsoleLogger logger = ConsoleLoggerFactory.get(WalkFlySpeedRestoreType.class);

    public abstract void restoreFlySpeed(Player player, LimboPlayer limbo);

    public abstract void restoreWalkSpeed(Player player, LimboPlayer limbo);

    private static void restoreNoZero(Player player, LimboPlayer limbo, boolean fly) {
        float speed = getSpeed(limbo, fly);
        if (speed > 0.01f) {
            log(player, fly, String.valueOf(speed), " (RESTORE_NO_ZERO mode)");
            setSpeed(player, fly, speed);
        } else {
            log(player, fly, "DEFAULT, it was 0!", " (RESTORE_NO_ZERO mode)");
            setSpeed(player, fly, fly ? LimboPlayer.DEFAULT_FLY_SPEED : LimboPlayer.DEFAULT_WALK_SPEED);
        }
    }

    private static void restoreMax(Player player, LimboPlayer limbo, boolean fly) {
        float current = fly ? player.getFlySpeed() : player.getWalkSpeed();
        float limboSpeed = getSpeed(limbo, fly);
        float newSpeed = Math.max(current, limboSpeed);
        log(player, fly, String.valueOf(newSpeed),
            " (Current: " + current + ", Limbo: " + limboSpeed + ") (MAX_RESTORE mode)");
        setSpeed(player, fly, newSpeed);
    }

    private static void restoreDefault(Player player, boolean fly) {
        log(player, fly, "DEFAULT", " (DEFAULT mode)");
        setSpeed(player, fly, fly ? LimboPlayer.DEFAULT_FLY_SPEED : LimboPlayer.DEFAULT_WALK_SPEED);
    }

    private static float getSpeed(LimboPlayer limbo, boolean fly) {
        return fly ? limbo.getFlySpeed() : limbo.getWalkSpeed();
    }

    private static void log(Player player, boolean fly, String target, String detail) {
        logger.debug(() -> "Restoring " + (fly ? "fly" : "walk") + " speed for LimboPlayer " + player.getName() + " to " + target + detail);
    }

    private static void setSpeed(Player player, boolean fly, float speed) {
        if (fly) {
            player.setFlySpeed(speed);
        } else {
            player.setWalkSpeed(speed);
        }
    }
}
