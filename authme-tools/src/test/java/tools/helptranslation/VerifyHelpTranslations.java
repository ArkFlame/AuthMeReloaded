package tools.helptranslation;

import tools.utils.ToolTask;
import tools.utils.ToolsConstants;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static tools.utils.FileIoUtils.listFilesOrThrow;

/**
 * Verifies the help translations for validity and completeness.
 */
public class VerifyHelpTranslations implements ToolTask {

    private static final Pattern HELP_MESSAGE_PATTERN = Pattern.compile("help_[a-z]{2,7}\\.yml");
    private static final String FOLDER = ToolsConstants.MAIN_RESOURCES_ROOT + "messages/";

    @Override
    public String getTaskName() {
        return "verifyHelpTranslations";
    }

    @Override
    public void execute(Scanner scanner) {
        System.out.println("Check specific language file?");
        System.out.println("Enter the language code for a specific file (e.g. 'it' for help_it.yml)");
        System.out.println("Empty line will check all files in the resources messages folder (default)");

        String language = scanner.nextLine();
        if (language.isEmpty()) {
            getHelpTranslations().forEach(this::processFile);
        } else {
            processFile(new File(FOLDER, "help_" + language + ".yml"));
        }
    }

    private void processFile(File file) {
        System.out.println("Checking '" + file.getName() + "'");
        HelpTranslationVerifier verifier = new HelpTranslationVerifier(file);

        // Check and output errors
        printIfNotEmpty("Missing sections", verifier.getMissingSections());
        printIfNotEmpty("Unknown sections", verifier.getUnknownSections());
        printIfNotEmpty("Missing command entries", verifier.getMissingCommands());
        printIfNotEmpty("Unknown command entries", verifier.getUnknownCommands());
    }

    private static void printIfNotEmpty(String label, List<String> values) {
        if (!values.isEmpty()) {
            System.out.println(label + ": " + String.join(", ", values));
        }
    }

    private static List<File> getHelpTranslations() {
        File[] files = listFilesOrThrow(new File(FOLDER));
        List<File> helpFiles = Arrays.stream(files)
            .filter(file -> HELP_MESSAGE_PATTERN.matcher(file.getName()).matches())
            .collect(Collectors.toList());
        if (helpFiles.isEmpty()) {
            throw new IllegalStateException("Could not get any matching files!");
        }
        return helpFiles;
    }
}
