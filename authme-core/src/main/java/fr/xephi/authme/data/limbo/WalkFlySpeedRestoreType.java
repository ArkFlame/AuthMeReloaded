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
            restore(player, true, limbo.getFlySpeed(), "RESTORE");
        }

        @Override
        public void restoreWalkSpeed(Player player, LimboPlayer limbo) {
            restore(player, false, limbo.getWalkSpeed(), "RESTORE");
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

    private static void restore(Player player, boolean fly, float speed, String mode) {
        logger.debug(() -> "Restoring " + type(fly) + " speed for LimboPlayer " + player.getName()
            + " to " + speed + " (" + mode + " mode)");
        setSpeed(player, fly, speed);
    }

    private static void restoreNoZero(Player player, LimboPlayer limbo, boolean fly) {
        float speed = getSpeed(limbo, fly);
        if (speed > 0.01f) {
            restore(player, fly, speed, "RESTORE_NO_ZERO");
        } else {
            logger.debug(() -> "Restoring " + type(fly) + " speed for LimboPlayer " + player.getName()
                + " to DEFAULT, it was 0! (RESTORE_NO_ZERO mode)");
            setSpeed(player, fly, fly ? LimboPlayer.DEFAULT_FLY_SPEED : LimboPlayer.DEFAULT_WALK_SPEED);
        }
    }

    private static void restoreMax(Player player, LimboPlayer limbo, boolean fly) {
        float current = fly ? player.getFlySpeed() : player.getWalkSpeed();
        float limboSpeed = getSpeed(limbo, fly);
        float newSpeed = Math.max(current, limboSpeed);
        logger.debug(() -> "Restoring " + type(fly) + " speed for LimboPlayer " + player.getName() + " to " + newSpeed
            + " (Current: " + current + ", Limbo: " + limboSpeed + ") (MAX_RESTORE mode)");
        setSpeed(player, fly, newSpeed);
    }

    private static void restoreDefault(Player player, boolean fly) {
        logger.debug(() -> "Restoring " + type(fly) + " speed for LimboPlayer " + player.getName()
            + " to DEFAULT (DEFAULT mode)");
        setSpeed(player, fly, fly ? LimboPlayer.DEFAULT_FLY_SPEED : LimboPlayer.DEFAULT_WALK_SPEED);
    }

    private static float getSpeed(LimboPlayer limbo, boolean fly) {
        return fly ? limbo.getFlySpeed() : limbo.getWalkSpeed();
    }

    private static String type(boolean fly) {
        return fly ? "fly" : "walk";
    }

    private static void setSpeed(Player player, boolean fly, float speed) {
        if (fly) {
            player.setFlySpeed(speed);
        } else {
            player.setWalkSpeed(speed);
        }
    }
}
