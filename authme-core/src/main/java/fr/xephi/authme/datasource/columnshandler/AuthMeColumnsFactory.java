package fr.xephi.authme.datasource.columnshandler;

import ch.jalu.configme.properties.Property;
import ch.jalu.datasourcecolumns.ColumnType;
import ch.jalu.datasourcecolumns.StandardTypes;
import fr.xephi.authme.data.auth.PlayerAuth;

import java.util.function.Function;

/**
 * Util class for initializing {@link DataSourceColumn} objects.
 */
final class AuthMeColumnsFactory {

    private AuthMeColumnsFactory() {
    }

    static DataSourceColumn<Integer> createInteger(Property<String> nameProperty,
                                                   ColumnOptions... options) {
        return new DataSourceColumn<>(StandardTypes.INTEGER, nameProperty,
            hasOption(ColumnOptions.OPTIONAL, options), hasOption(ColumnOptions.DEFAULT_FOR_NULL, options));
    }

    static PlayerAuthColumn<Integer> createInteger(Property<String> nameProperty,
                                                   Function<PlayerAuth, Integer> playerAuthGetter,
                                                   ColumnOptions... options) {
        return createInternal(StandardTypes.INTEGER, nameProperty, playerAuthGetter, options);
    }

    static PlayerAuthColumn<Long> createLong(Property<String> nameProperty,
                                             Function<PlayerAuth, Long> playerAuthGetter,
                                             ColumnOptions... options) {
        return createInternal(StandardTypes.LONG, nameProperty, playerAuthGetter, options);
    }

    static PlayerAuthColumn<String> createString(Property<String> nameProperty,
                                                 Function<PlayerAuth, String> playerAuthGetter,
                                                 ColumnOptions... options) {
        return createInternal(StandardTypes.STRING, nameProperty, playerAuthGetter, options);
    }

    static PlayerAuthColumn<Double> createDouble(Property<String> nameProperty,
                                                 Function<PlayerAuth, Double> playerAuthGetter,
                                                 ColumnOptions... options) {
        return createInternal(StandardTypes.DOUBLE, nameProperty, playerAuthGetter, options);
    }

    static PlayerAuthColumn<Float> createFloat(Property<String> nameProperty,
                                               Function<PlayerAuth, Float> playerAuthGetter,
                                               ColumnOptions... options) {
        return createInternal(StandardTypes.FLOAT, nameProperty, playerAuthGetter, options);
    }

    private static <T> PlayerAuthColumn<T> createInternal(ColumnType<T> type, Property<String> nameProperty,
                                                          Function<PlayerAuth, T> authGetter,
                                                          ColumnOptions... options) {
        return new PlayerAuthColumn<>(type, nameProperty,
            hasOption(ColumnOptions.OPTIONAL, options), hasOption(ColumnOptions.DEFAULT_FOR_NULL, options), authGetter);
    }

    private static boolean hasOption(ColumnOptions needle, ColumnOptions[] options) {
        for (ColumnOptions option : options) {
            if (option == needle) {
                return true;
            }
        }
        return false;
    }

    enum ColumnOptions {

        OPTIONAL,

        DEFAULT_FOR_NULL
    }
}
