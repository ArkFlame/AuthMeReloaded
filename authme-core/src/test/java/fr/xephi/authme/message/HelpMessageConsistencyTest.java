package fr.xephi.authme.message;

import ch.jalu.configme.resource.PropertyReader;
import ch.jalu.configme.resource.YamlFileReader;
import fr.xephi.authme.TestHelper;
import fr.xephi.authme.command.help.HelpMessage;
import fr.xephi.authme.command.help.HelpSection;
import fr.xephi.authme.permission.DefaultPermission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static fr.xephi.authme.message.MessagePathHelper.MESSAGES_FOLDER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.not;

/**
 * Tests that all help_xx.yml files contain all entries for
 * {@link HelpSection}, {@link HelpMessage} and {@link DefaultPermission}.
 */
class HelpMessageConsistencyTest {

    private List<File> helpFiles;

    @BeforeEach
    void findHelpMessagesFiles() {
        File folder = TestHelper.getJarFile("/" + MESSAGES_FOLDER);
        File[] files = folder.listFiles();
        if (files == null || files.length == 0) {
            throw new IllegalStateException("Could not get files from '" + MESSAGES_FOLDER + "'");
        }
        helpFiles = Arrays.stream(files)
            .filter(file -> MessagePathHelper.isHelpFile(file.getName()))
            .collect(Collectors.toList());
    }

    @Test
    void shouldHaveRequiredEntries() {
        List<String> keys = getRequiredKeys();
        for (File file : helpFiles) {
            PropertyReader reader = new YamlFileReader(file);
            for (String key : keys) {
                assertThat(file.getName() + " should have entry for '" + key + "'",
                    reader.getString(key), not(emptyOrNullString()));
            }
        }
    }

    private static List<String> getRequiredKeys() {
        List<String> keys = new ArrayList<>();
        for (HelpSection section : HelpSection.values()) {
            keys.add(section.getKey());
        }
        for (HelpMessage message : HelpMessage.values()) {
            keys.add(message.getKey());
        }
        for (DefaultPermission permission : DefaultPermission.values()) {
            keys.add(getPathForDefaultPermission(permission));
        }
        return keys;
    }

    private static String getPathForDefaultPermission(DefaultPermission defaultPermission) {
        String path = "common.defaultPermissions.";
        switch (defaultPermission) {
            case ALLOWED:
                return path + "allowed";
            case NOT_ALLOWED:
                return path + "notAllowed";
            case OP_ONLY:
                return path + "opOnly";
            default:
                throw new IllegalStateException("Unknown default permission '" + defaultPermission + "'");
        }
    }
}
