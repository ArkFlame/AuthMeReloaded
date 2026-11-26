package fr.xephi.authme.command.executable.authme;

import ch.jalu.injector.factory.Factory;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSortedMap;
import fr.xephi.authme.ConsoleLogger;
import fr.xephi.authme.command.ExecutableCommand;
import fr.xephi.authme.datasource.converter.AuthPlusConverter;
import fr.xephi.authme.datasource.converter.Converter;
import fr.xephi.authme.datasource.converter.CrazyLoginConverter;
import fr.xephi.authme.datasource.converter.LibreLoginConverter;
import fr.xephi.authme.datasource.converter.LimboAuthConverter;
import fr.xephi.authme.datasource.converter.LoginSecurityConverter;
import fr.xephi.authme.datasource.converter.MySqlToSqlite;
import fr.xephi.authme.datasource.converter.NexAuthConverter;
import fr.xephi.authme.datasource.converter.NLoginConverter;
import fr.xephi.authme.datasource.converter.OpeNLoginConverter;
import fr.xephi.authme.datasource.converter.RoyalAuthConverter;
import fr.xephi.authme.datasource.converter.SqliteToSql;
import fr.xephi.authme.datasource.converter.TiAuthConverter;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.output.ConsoleLoggerFactory;
import fr.xephi.authme.service.BukkitService;
import fr.xephi.authme.service.CommonService;
import org.bukkit.command.CommandSender;

import javax.inject.Inject;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Converter command: launches conversion based on its parameters.
 */
public class ConverterCommand implements ExecutableCommand {

    @VisibleForTesting
    static final Map<String, Class<? extends Converter>> CONVERTERS = getConverters();

    private final ConsoleLogger logger = ConsoleLoggerFactory.get(ConverterCommand.class);

    @Inject
    private CommonService commonService;

    @Inject
    private BukkitService bukkitService;

    @Inject
    private Factory<Converter> converterFactory;

    @Override
    public void executeCommand(CommandSender sender, List<String> arguments) {
        Class<? extends Converter> converterClass = arguments.isEmpty()
            ? null
            : CONVERTERS.get(arguments.get(0).toLowerCase(Locale.ROOT));
        if (converterClass == null) {
            sender.sendMessage("Converters: " + String.join(", ", CONVERTERS.keySet()));
            return;
        }

        final Converter converter = converterFactory.newInstance(converterClass);
        bukkitService.runTaskAsynchronously(() -> {
            try {
                converter.execute(sender);
            } catch (Exception e) {
                commonService.send(sender, MessageKey.ERROR);
                logger.logException("Error during conversion:", e);
            }
        });

        sender.sendMessage("[AuthMe] Successfully started " + arguments.get(0));
    }

    /**
     * Initializes a map with all available converters.
     *
     * @return map with all available converters
     */
    private static Map<String, Class<? extends Converter>> getConverters() {
        return ImmutableSortedMap.<String, Class<? extends Converter>>naturalOrder()
            .put("authplus", AuthPlusConverter.class)
            .put("crazylogin", CrazyLoginConverter.class)
            .put("librelogin", LibreLoginConverter.class)
            .put("limboauth", LimboAuthConverter.class)
            .put("loginsecurity", LoginSecurityConverter.class)
            .put("nexauth", NexAuthConverter.class)
            .put("nlogin", NLoginConverter.class)
            .put("openlogin", OpeNLoginConverter.class)
            .put("royalauth", RoyalAuthConverter.class)
            .put("tiauth", TiAuthConverter.class)
            .put("sqlitetosql", SqliteToSql.class)
            .put("mysqltosqlite", MySqlToSqlite.class)
            .build();
    }
}
