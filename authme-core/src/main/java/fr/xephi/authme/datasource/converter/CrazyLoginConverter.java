/*
 * Ported from HaHaWTH/AuthMeReReloaded (AGPL-3.0), file
 * src/main/java/fr/xephi/authme/datasource/converter/CrazyLoginConverter.java.
 * Copyright (c) HaHaWTH contributors. Original licence retained; not relicensed.
 */

package fr.xephi.authme.datasource.converter;

import fr.xephi.authme.ConsoleLogger;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.initialization.DataFolder;
import fr.xephi.authme.output.ConsoleLoggerFactory;
import fr.xephi.authme.settings.Settings;
import fr.xephi.authme.settings.properties.ConverterSettings;
import org.bukkit.command.CommandSender;

import javax.inject.Inject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Locale;

/**
 * Converter for CrazyLogin to AuthMe. CrazyLogin stores passwords in plain text, so the
 * value is imported verbatim as the stored hash: affected players must change their
 * password after the conversion.
 */
public class CrazyLoginConverter implements Converter {

    private final ConsoleLogger logger = ConsoleLoggerFactory.get(CrazyLoginConverter.class);

    private final DataSource database;
    private final Settings settings;
    private final File dataFolder;

    @Inject
    CrazyLoginConverter(@DataFolder File dataFolder, DataSource dataSource, Settings settings) {
        this.dataFolder = dataFolder;
        this.database = dataSource;
        this.settings = settings;
    }

    @Override
    public void execute(CommandSender sender) {
        String fileName = settings.getProperty(ConverterSettings.CRAZYLOGIN_FILE_NAME);
        File source = new File(dataFolder, fileName);
        if (!source.exists()) {
            sender.sendMessage("CrazyLogin file not found, please put " + fileName + " in AuthMe folder!");
            return;
        }

        String line;
        try (BufferedReader users = new BufferedReader(new FileReader(source))) {
            while ((line = users.readLine()) != null) {
                String[] args = line.split("\\|");
                if (args.length < 2 || "name".equalsIgnoreCase(args[0])) {
                    continue;
                }
                String playerName = args[0];
                String lowerCaseName = playerName.toLowerCase(Locale.ROOT);
                if (database.isAuthAvailable(lowerCaseName)) {
                    continue;
                }
                database.saveAuth(PlayerAuth.builder()
                    .name(lowerCaseName)
                    .realName(playerName)
                    .password(args[1], null)
                    .build());
            }
            logger.info("CrazyLogin database has been imported correctly");
        } catch (IOException ex) {
            logger.warning("Can't open the crazylogin database file! Does it exist?");
            logger.logException("Encountered", ex);
        }
    }
}
