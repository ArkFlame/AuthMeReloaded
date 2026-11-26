package fr.xephi.authme.command.executable.authme.debug;

import fr.xephi.authme.ConsoleLogger;
import fr.xephi.authme.data.limbo.LimboService;
import fr.xephi.authme.datasource.CacheDataSource;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.output.ConsoleLoggerFactory;
import org.bukkit.Location;
import org.bukkit.World;

import java.lang.reflect.Field;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Utilities used within the DebugSection implementations.
 */
final class DebugSectionUtils {

    private static ConsoleLogger logger = ConsoleLoggerFactory.get(DebugSectionUtils.class);
    private static Field limboEntriesField;
    private static final ThreadLocal<DecimalFormat> DECIMAL_FORMAT = ThreadLocal.withInitial(() -> {
        DecimalFormat df = new DecimalFormat("#.##");
        df.setDecimalFormatSymbols(DecimalFormatSymbols.getInstance(Locale.US));
        df.setRoundingMode(RoundingMode.HALF_UP);
        return df;
    });

    private DebugSectionUtils() {
    }

    /**
     * Formats the given location in a human readable way. Null-safe.
     *
     * @param location the location to format
     * @return the formatted location
     */
    static String formatLocation(Location location) {
        if (location == null) {
            return "null";
        }

        World world = location.getWorld();
        String worldName = world == null ? "null" : world.getName();
        return formatLocation(location.getX(), location.getY(), location.getZ(), worldName);
    }

    /**
     * Formats the given location in a human readable way.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     * @param z the z coordinate
     * @param world the world name
     * @return the formatted location
     */
    static String formatLocation(double x, double y, double z, String world) {
        DecimalFormat format = DECIMAL_FORMAT.get();
        return "(" + format.format(x) + ", " + format.format(y) + ", " + format.format(z)
            + ") in '" + world + "'";
    }

    /**
     * Applies the given function to the map in LimboService containing the LimboPlayers.
     * As we don't want to expose this information in non-debug settings, this is done with reflection.
     * Exceptions are generously caught and {@code null} is returned on failure.
     *
     * @param limboService the limbo service instance to get the map from
     * @param function the function to apply to the map
     * @param <U> the result type of the function
     *
     * @return the value of the function applied to the map, or null upon error
     */
    static <U> U applyToLimboPlayersMap(LimboService limboService, Function<Map, U> function) {
        if (limboEntriesField == null) {
            try {
                Field field = LimboService.class.getDeclaredField("entries");
                field.setAccessible(true);
                limboEntriesField = field;
            } catch (Exception e) {
                logger.logException("Could not retrieve LimboService entries field:", e);
                return null;
            }
        }
        try {
            return function.apply((Map) limboEntriesField.get(limboService));
        } catch (Exception e) {
            logger.logException("Could not retrieve LimboService values:", e);
            return null;
        }
    }

    static <T> T castToTypeOrNull(Object object, Class<T> clazz) {
        return clazz.isInstance(object) ? clazz.cast(object) : null;
    }

    /** Returns the enum constant of the given type whose name matches {@code name}, ignoring case, or null. */
    static <E extends Enum<E>> E matchEnumByName(Class<E> clazz, String name) {
        return Arrays.stream(clazz.getEnumConstants())
            .filter(e -> e.name().equalsIgnoreCase(name))
            .findFirst().orElse(null);
    }

    /**
     * Unwraps the "cache data source" and returns the underlying source. Returns the
     * same as the input argument otherwise.
     *
     * @param dataSource the data source to unwrap if applicable
     * @return the non-cache data source
     */
    static DataSource unwrapSourceFromCacheDataSource(DataSource dataSource) {
        if (dataSource instanceof CacheDataSource) {
            try {
                Field source = CacheDataSource.class.getDeclaredField("source");
                source.setAccessible(true);
                return (DataSource) source.get(dataSource);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                logger.logException("Could not get source of CacheDataSource:", e);
                return null;
            }
        }
        return dataSource;
    }
}
